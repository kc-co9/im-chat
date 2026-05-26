package com.co.kc.imchat.infrastructure.support.notifier.confirmation;

import com.co.kc.imchat.application.support.notifier.confirmable.ImMessageConfirmableStore;
import com.co.kc.imchat.application.support.notifier.task.ReceiptTask;
import com.co.kc.imchat.common.utils.JsonUtils;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
public class RedisImMessageConfirmableStore implements ImMessageConfirmableStore {
    /**
     * 单个 receiptId 允许执行的最大重投次数，不包含首次通知。
     */
    private static final int MAX_REDELIVER_ATTEMPTS = 3;
    /**
     * 任务内容比队列延迟多保留一段时间，避免队列到期后任务内容过早过期。
     */
    private static final long RECEIPT_TASK_GRACE_MILLIS = 60_000L;
    /**
     * 单次从阻塞队列拉取到期任务的最长等待时间。
     */
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(500L);

    /**
     * 回执重投任务队列，内部用延迟队列和阻塞队列组合实现。
     */
    private final ReceiptTaskQueue receiptTaskQueue;
    /**
     * 每个 receiptId 的重投次数，用来限制最大重投次数。
     */
    private final RMapCache<String, Integer> receiptAttempts;

    public RedisImMessageConfirmableStore(RedissonClient redissonClient) {
        this.receiptTaskQueue = new ReceiptTaskQueue(redissonClient);
        this.receiptAttempts = redissonClient.getMapCache(ReceiptTaskKeys.ATTEMPTS, StringCodec.INSTANCE);
    }

    @Override
    public void offer(ReceiptTask task) {
        receiptTaskQueue.offer(task.getReceiptId(), JsonUtils.toJson(task), task.getDelayMillis());
    }

    @Override
    public void consume(Consumer<ReceiptTask> consumer) {
        String receiptId = receiptTaskQueue.poll(POLL_TIMEOUT);
        if (receiptId == null) {
            return;
        }
        String taskJson = receiptTaskQueue.get(receiptId);
        if (taskJson == null) {
            clearAttempts(receiptId);
            return;
        }
        ReceiptTask task = JsonUtils.fromJson(taskJson, ReceiptTask.class);
        int attempts = incrementAttempts(task);
        // 超过上限后丢弃当前任务，不再写入下一轮延迟任务。
        if (attempts > MAX_REDELIVER_ATTEMPTS) {
            receiptTaskQueue.ack(receiptId);
            clearAttempts(task.getReceiptId());
            return;
        }
        try {
            consumer.accept(task);
            // 当前简化模型先删除任务内容再写入下一轮延迟任务，两步之间进程崩溃会丢失后续重试。
            receiptTaskQueue.ack(receiptId);
            offer(task);
        } catch (Exception e) {
            receiptTaskQueue.nack(receiptId, task.getDelayMillis());
            throw e;
        }
    }

    @Override
    public void confirm(String receiptId) {
        receiptTaskQueue.remove(receiptId);
        clearAttempts(receiptId);
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
     * <p>receiptId 是单个回执任务的唯一标识，队列里只存 receiptId；
     * 任务内容保存在 receiptTasks 里，用 receiptId 读取和清理。</p>
     *
     * <p>当前实现会在消费失败时重新延迟投递 receiptId，但不提供 poll 之后进程崩溃的强可靠保证。</p>
     */
    private static final class ReceiptTaskQueue {
        /**
         * 存放到期回执重投任务的阻塞队列。
         */
        private final RBlockingDeque<String> receiptQueue;
        /**
         * 负责将回执任务延迟转移到阻塞队列。
         */
        @SuppressWarnings("deprecation")
        private final RDelayedQueue<String> receiptDelayedQueue;
        /**
         * receiptId -> 回执任务内容。
         * 队列元素只保存 receiptId，消费时从这里读取完整任务。
         */
        private final RMapCache<String, String> receiptTasks;

        private ReceiptTaskQueue(RedissonClient redissonClient) {
            this.receiptQueue = redissonClient.getBlockingDeque(ReceiptTaskKeys.QUEUE, StringCodec.INSTANCE);
            this.receiptDelayedQueue = redissonClient.getDelayedQueue(receiptQueue);
            this.receiptTasks = redissonClient.getMapCache(ReceiptTaskKeys.TASKS, StringCodec.INSTANCE);
        }

        private String get(String receiptId) {
            return receiptTasks.get(receiptId);
        }

        /**
         * 保存任务内容，并把 receiptId 写入延迟队列。
         */
        private void offer(String receiptId, String taskJson, long delayMillis) {
            receiptTasks.fastPut(receiptId, taskJson, taskTtl(delayMillis).toMillis(), TimeUnit.MILLISECONDS);
            receiptDelayedQueue.offer(receiptId, delayMillis, TimeUnit.MILLISECONDS);
        }

        @SuppressWarnings("SameParameterValue")
        private String poll(Duration timeout) {
            try {
                return receiptQueue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
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
            if (receiptTasks.containsKey(receiptId)) {
                receiptTasks.expireEntry(receiptId, taskTtl(delayMillis), Duration.ZERO);
                receiptDelayedQueue.offer(receiptId, delayMillis, TimeUnit.MILLISECONDS);
            }
        }

        /**
         * 按 receiptId 删除队列中的未消费任务。
         * DelayedQueue 未到期时从 delayedQueue 删除，到期后从 receiptQueue 删除。
         */
        private void remove(String receiptId) {
            receiptTasks.fastRemove(receiptId);
            receiptDelayedQueue.remove(receiptId);
            receiptQueue.remove(receiptId);
        }

    }

    private static final class ReceiptTaskKeys {
        /**
         * DelayedQueue 存放延迟回执任务。
         * 任务到期后由 consume 拉取，业务重投成功后再写入下一次延迟任务。
         */
        private static final String QUEUE = "im:message:receipt:delay";
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
    }
}
