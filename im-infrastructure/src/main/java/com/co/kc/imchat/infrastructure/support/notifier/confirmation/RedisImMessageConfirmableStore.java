package com.co.kc.imchat.infrastructure.support.notifier.confirmation;

import com.co.kc.imchat.application.support.notifier.confirmable.ImMessageConfirmableStore;
import com.co.kc.imchat.application.support.notifier.task.ReceiptTask;
import com.co.kc.imchat.application.support.notifier.task.ReceiptType;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
@Component
public class RedisImMessageConfirmableStore implements ImMessageConfirmableStore {
    private static final ThreadFactory THREAD_FACTORY = new ThreadFactoryBuilder()
            .setNameFormat("im-message-confirmable-store-%d")
            .setDaemon(false)
            .setPriority(Thread.NORM_PRIORITY)
            .build();

    /**
     * 单个 receiptId 允许执行的最大重投次数，不包含首次通知。
     */
    private static final int MAX_REDELIVER_ATTEMPTS = 3;
    /**
     * 任务内容比队列延迟多保留一段时间，避免队列到期后任务内容过早过期。
     */
    private static final long RECEIPT_TASK_GRACE_MILLIS = 60_000L;
    /**
     * 单次从分片队列拉取到期任务的等待时间。
     *
     * <p>每个分片队列独立消费，短暂等待可以减少空队列轮询时的无效 Redis 请求。</p>
     */
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(100L);

    private static final int RECEIPT_QUEUE_SIZE = ReceiptType.values().length;
    private static final int RECEIPT_QUEUE_SHARD_SIZE = 16;
    private static final int MAX_CONSUME_BATCH_SIZE = 10;


    /**
     * 回执重投任务队列，内部按 ReceiptType 和 receiptId hash 拆分为多组延迟队列。
     */
    private final ReceiptTaskQueue receiptTaskQueue;
    /**
     * 每个 receiptId 的重投次数，用来限制最大重投次数。
     */
    private final RMapCache<String, Integer> receiptAttempts;

    /**
     * 回执重投任务消费执行器。
     */
    private final ScheduledExecutorService scheduledExecutorService;
    private final AtomicBoolean confirmingStarted = new AtomicBoolean(false);

    @Autowired
    public RedisImMessageConfirmableStore(RedissonClient redissonClient) {
        this(redissonClient,
                new ScheduledThreadPoolExecutor(
                        RECEIPT_QUEUE_SIZE * RECEIPT_QUEUE_SHARD_SIZE,
                        THREAD_FACTORY,
                        new ThreadPoolExecutor.DiscardPolicy()));
    }

    @VisibleForTesting
    RedisImMessageConfirmableStore(RedissonClient redissonClient, ScheduledExecutorService scheduledExecutorService) {
        this.receiptTaskQueue = new ReceiptTaskQueue(redissonClient, RECEIPT_QUEUE_SIZE, RECEIPT_QUEUE_SHARD_SIZE);
        this.receiptAttempts = redissonClient.getMapCache(ReceiptTaskKeys.ATTEMPTS, StringCodec.INSTANCE);
        this.scheduledExecutorService = scheduledExecutorService;
    }

    @Override
    public void offer(ReceiptTask task) {
        receiptTaskQueue.offer(task);
    }

    @Override
    public void startConfirming(Consumer<ReceiptTask> consumer) {
        if (!confirmingStarted.compareAndSet(false, true)) {
            log.warn("消息确认重试任务已启动，忽略重复启动请求");
            return;
        }
        for (int i = 0; i < RECEIPT_QUEUE_SIZE; i++) {
            for (int j = 0; j < RECEIPT_QUEUE_SHARD_SIZE; j++) {
                int queue = i;
                int shard = j;
                scheduledExecutorService.scheduleWithFixedDelay(() -> {
                    try {
                        consumeBatch(queue, shard, consumer);
                    } catch (Exception ex) {
                        log.error("消费消息确认重试任务失败", ex);
                    }
                }, 1, 1, TimeUnit.SECONDS);
            }
        }

    }

    @Override
    public void stopConfirming() {
        scheduledExecutorService.shutdown();
    }

    @Override
    public void confirm(String receiptId) {
        receiptTaskQueue.remove(receiptId);
        clearAttempts(receiptId);
    }

    private void consumeBatch(int queue, int shard, Consumer<ReceiptTask> consumer) {
        for (int i = 0; i < MAX_CONSUME_BATCH_SIZE; i++) {
            if (!consume(queue, shard, consumer)) {
                return;
            }
        }
    }

    private boolean consume(int queue, int shard, Consumer<ReceiptTask> consumer) {
        String receiptId = receiptTaskQueue.poll(queue, shard, POLL_TIMEOUT);
        if (receiptId == null) {
            return false;
        }
        ReceiptTask task = receiptTaskQueue.get(receiptId);
        if (task == null) {
            clearAttempts(receiptId);
            return true;
        }
        int attempts = incrementAttempts(task);
        // 超过上限后丢弃当前任务，不再写入下一轮延迟任务。
        if (attempts > MAX_REDELIVER_ATTEMPTS) {
            receiptTaskQueue.ack(receiptId);
            clearAttempts(task.getReceiptId());
            return true;
        }
        try {
            consumer.accept(task);
            // 当前简化模型先删除任务内容再写入下一轮延迟任务，两步之间进程崩溃会丢失后续重试。
            receiptTaskQueue.ack(receiptId);
            offer(task);
            return true;
        } catch (Exception e) {
            receiptTaskQueue.nack(receiptId, task.getDelayMillis());
            throw e;
        }
    }

    private int incrementAttempts(ReceiptTask task) {
        int attempts = receiptAttempts.addAndGet(task.getReceiptId(), 1);
        receiptAttempts.expireEntry(task.getReceiptId(), taskTtl(task.getDelayMillis()), Duration.ZERO);
        return attempts;
    }

    private static Duration taskTtl(long delayMillis) {
        return Duration.ofMillis(delayMillis + RECEIPT_TASK_GRACE_MILLIS);
    }

    private void clearAttempts(String receiptId) {
        receiptAttempts.fastRemove(receiptId);
    }

    /**
     * 回执任务队列适配器。
     *
     * <p>队列按 {@link ReceiptType} 和 {@code receiptId} 分片。队列元素只存 receiptId，
     * 任务内容保存在 receiptTasks 里，用 receiptId 读取和清理。
     * 当前业务保证 receiptId 全局唯一。</p>
     *
     * <p>当前实现会在消费失败时重新延迟投递 receiptId，但不提供 poll 之后进程崩溃的强可靠保证。</p>
     */
    private static final class ReceiptTaskQueue {
        private final int queueSize;
        /**
         * 每个 ReceiptType 下按 receiptId 拆出的延迟队列数量。
         */
        private final int shardSize;

        /**
         * 按 receiptType 和 receiptId 分片后的队列组。
         */
        private final ReceiptTaskShardQueue[][] shardQueues;
        /**
         * receiptId -> 回执任务内容。
         * 队列元素只保存 receiptId，消费时从这里读取完整任务。
         */
        private final RMapCache<String, String> receiptTasks;

        private ReceiptTaskQueue(RedissonClient redissonClient, int queueSize, int shardSize) {
            this.queueSize = queueSize;
            this.shardSize = shardSize;
            this.shardQueues = this.buildShardQueues(redissonClient);
            this.receiptTasks = redissonClient.getMapCache(ReceiptTaskKeys.TASKS, StringCodec.INSTANCE);
        }

        private ReceiptTaskShardQueue[][] buildShardQueues(RedissonClient redissonClient) {
            ReceiptTaskShardQueue[][] queues = new ReceiptTaskShardQueue[queueSize][shardSize];
            for (int i = 0; i < queueSize; i++) {
                for (int j = 0; j < shardSize; j++) {
                    queues[i][j] = new ReceiptTaskShardQueue(redissonClient, i, j);
                }
            }
            return queues;
        }

        private ReceiptTask get(String receiptId) {
            String taskJson = receiptTasks.get(receiptId);
            if (StringUtils.isBlank(taskJson)) {
                return null;
            }
            return JsonUtils.fromJson(taskJson, ReceiptTask.class);
        }

        /**
         * 保存任务内容，并把 receiptId 写入延迟队列。
         */
        private void offer(ReceiptTask task) {
            receiptTasks.fastPut(task.getReceiptId(), JsonUtils.toJson(task), taskTtl(task.getDelayMillis()).toMillis(), TimeUnit.MILLISECONDS);
            shardQueues[queueIdxOf(task)][shardIdxOf(task)].offer(task.getReceiptId(), task.getDelayMillis());
        }

        @SuppressWarnings("SameParameterValue")
        private String poll(int queueIdx, int shardIdx, Duration timeout) {
            return shardQueues[queueIdx][shardIdx].poll(timeout);
        }

        /**
         * 成功处理当前任务后，删除任务内容。
         */
        private void ack(String receiptId) {
            receiptTasks.fastRemove(receiptId);
        }

        /**
         * 业务处理失败时重新延迟投递。
         * 如果任务内容已经被确认删除，则不再重投。
         */
        private void nack(String receiptId, long delayMillis) {
            ReceiptTask task = get(receiptId);
            if (task != null) {
                receiptTasks.expireEntry(receiptId, taskTtl(delayMillis), Duration.ZERO);
                shardQueues[queueIdxOf(task)][shardIdxOf(task)].offer(receiptId, delayMillis);
            }
        }

        /**
         * 按 receiptType 和 receiptId 定位分片，删除队列中的未消费任务。
         * DelayedQueue 未到期时从 delayedQueue 删除，到期后从 receiptQueue 删除。
         */
        private void remove(String receiptId) {
            ReceiptTask task = get(receiptId);
            if (task != null) {
                receiptTasks.fastRemove(receiptId);
                shardQueues[queueIdxOf(task)][shardIdxOf(task)].remove(receiptId);
            }
        }

        private int queueIdxOf(ReceiptTask task) {
            return task.getReceiptType().ordinal() % queueSize;
        }

        private int shardIdxOf(ReceiptTask task) {
            return Math.floorMod(task.getReceiptId().hashCode(), shardSize);
        }
    }

    private static class ReceiptTaskShardQueue {
        /**
         * 存放到期回执重投任务的阻塞队列
         */
        private final RBlockingDeque<String> receiptQueue;

        /**
         * 负责将回执任务延迟转移到阻塞队列。
         */
        @SuppressWarnings("deprecation")
        private final RDelayedQueue<String> receiptDelayedQueue;

        public ReceiptTaskShardQueue(RedissonClient redissonClient, int queueIdx, int shardIdx) {
            String queueName = ReceiptTaskKeys.queue(queueIdx, shardIdx);
            receiptQueue = redissonClient.getBlockingDeque(queueName, StringCodec.INSTANCE);
            receiptDelayedQueue = redissonClient.getDelayedQueue(receiptQueue);
        }


        private void offer(String receiptId, long delayMillis) {
            receiptDelayedQueue.offer(receiptId, delayMillis, TimeUnit.MILLISECONDS);
        }

        private String poll(Duration timeout) {
            try {
                return receiptQueue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        private void remove(String receiptId) {
            receiptDelayedQueue.remove(receiptId);
            receiptQueue.remove(receiptId);
        }
    }

    private static final class ReceiptTaskKeys {
        /**
         * 分片延迟队列 key 前缀，实际队列按 receiptType 和 shardIdx 追加后缀。
         */
        private static final String QUEUE_PREFIX = "im:message:receipt:delay";
        /**
         * receiptId -> 回执任务内容。
         * 队列只保存 receiptId，消费时通过这个 Map 读取完整任务。
         */
        private static final String TASKS = "im:message:receipt:delay:tasks";
        /**
         * receiptId -> 已重投次数。
         * 重试次数不放在 ReceiptTask 里，避免任务被重新序列化时携带状态，
         * 由 Redis 统一记录并设置 TTL。
         */
        private static final String ATTEMPTS = "im:message:receipt:delay:attempts";

        private ReceiptTaskKeys() {
        }

        private static String queue(int queueIdx, int shardIdx) {
            return QUEUE_PREFIX + ":" + queueIdx + ":" + shardIdx;
        }
    }
}
