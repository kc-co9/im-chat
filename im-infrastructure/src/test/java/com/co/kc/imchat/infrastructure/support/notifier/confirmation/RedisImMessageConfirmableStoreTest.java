package com.co.kc.imchat.infrastructure.support.notifier.confirmation;

import com.co.kc.imchat.application.support.notifier.task.ReceiptTask;
import com.co.kc.imchat.application.support.notifier.task.ReceiptType;
import com.co.kc.imchat.common.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisImMessageConfirmableStoreTest {
    private static final long POLL_TIMEOUT_MILLIS = 100L;

    @Test
    void offerAddsDelayedReceiptIdAndStoresTask() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> receiptQueue = mockQueue(redissonClient, ReceiptType.PRIVATE_MESSAGE_SEND, "ack:1");
        RDelayedQueue<String> delayedReceiptQueue = mockDelayedQueue(redissonClient, receiptQueue);
        RMapCache<String, String> receiptTasks = mockTasks(redissonClient);
        RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient);
        ReceiptTask task = task();
        String taskJson = JsonUtils.toJson(task);

        store.offer(task);

        verify(receiptTasks).fastPut(
                eq("ack:1"),
                eq(taskJson),
                longThat(ttl -> ttl >= 60_000L),
                eq(TimeUnit.MILLISECONDS));
        verify(delayedReceiptQueue).offer("ack:1", 2000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void offerRoutesDelayedReceiptIdByReceiptTypeAndReceiptIdShard() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> sendQueue = mockQueue(redissonClient, ReceiptType.PRIVATE_MESSAGE_SEND, "same:receipt");
        RBlockingDeque<String> revokeQueue = mockQueue(redissonClient, ReceiptType.GROUP_MESSAGE_REVOKE, "same:receipt");
        RDelayedQueue<String> sendDelayedQueue = mockDelayedQueue(redissonClient, sendQueue);
        RDelayedQueue<String> revokeDelayedQueue = mockDelayedQueue(redissonClient, revokeQueue);
        mockTasks(redissonClient);
        RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient);

        store.offer(task("same:receipt", ReceiptType.PRIVATE_MESSAGE_SEND));
        store.offer(task("same:receipt", ReceiptType.GROUP_MESSAGE_REVOKE));

        verify(sendDelayedQueue).offer("same:receipt", 2000L, TimeUnit.MILLISECONDS);
        verify(revokeDelayedQueue).offer("same:receipt", 2000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void offerStoresTaskWithoutDeduplication() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> receiptQueue = mockQueue(redissonClient, ReceiptType.PRIVATE_MESSAGE_SEND, "ack:1");
        RDelayedQueue<String> delayedReceiptQueue = mockDelayedQueue(redissonClient, receiptQueue);
        RMapCache<String, String> receiptTasks = mockTasks(redissonClient);
        ReceiptTask task = task();
        String taskJson = JsonUtils.toJson(task);
        RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient);

        store.offer(task);
        store.offer(task);

        verify(receiptTasks, times(2)).fastPut(
                eq("ack:1"),
                eq(taskJson),
                longThat(ttl -> ttl >= 60_000L),
                eq(TimeUnit.MILLISECONDS));
        verify(delayedReceiptQueue, times(2)).offer("ack:1", 2000L, TimeUnit.MILLISECONDS);
        verify(receiptQueue, never()).poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Test
    void initAcknowledgesQueueMessageAfterConsumerSucceeds() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> receiptQueue = mockQueue(redissonClient, ReceiptType.PRIVATE_MESSAGE_SEND, "ack:1");
        RDelayedQueue<String> delayedReceiptQueue = mockDelayedQueue(redissonClient, receiptQueue);
        RMapCache<String, String> receiptTasks = mockTasks(redissonClient);
        RMapCache<String, Integer> receiptAttempts = mockAttempts(redissonClient);
        when(receiptAttempts.addAndGet("ack:1", 1)).thenReturn(1);
        ReceiptTask task = task();
        String taskJson = JsonUtils.toJson(task);
        poll(receiptQueue, "ack:1");
        when(receiptTasks.get("ack:1")).thenReturn(taskJson);
        RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient);
        AtomicReference<ReceiptTask> consumedTask = new AtomicReference<>();

        consume(store, ReceiptType.PRIVATE_MESSAGE_SEND, "ack:1", consumedTask::set);

        assertThat(consumedTask.get().getReceiptId()).isEqualTo("ack:1");
        assertThat(consumedTask.get().getReceiptType()).isEqualTo(ReceiptType.PRIVATE_MESSAGE_SEND);
        verify(receiptTasks).fastRemove("ack:1");
        verify(receiptTasks).fastPut(eq("ack:1"), eq(taskJson), longThat(ttl -> ttl >= 60_000L), eq(TimeUnit.MILLISECONDS));
        verify(delayedReceiptQueue).offer("ack:1", 2000L, TimeUnit.MILLISECONDS);
        verify(receiptAttempts).addAndGet("ack:1", 1);
        verify(receiptAttempts).expireEntry(eq("ack:1"), any(), any());
        org.mockito.Mockito.verify(receiptAttempts, org.mockito.Mockito.never()).fastRemove("ack:1");
    }

    @Test
    void initOnlyPollsQueuesForAssignedReceiptType() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> privateQueue = mockQueue(redissonClient, ReceiptType.PRIVATE_MESSAGE_SEND, "ack:1");
        RBlockingDeque<String> groupQueue = mockQueue(redissonClient, ReceiptType.GROUP_MESSAGE_SEND, "ack:1");
        mockDelayedQueue(redissonClient, privateQueue);
        mockDelayedQueue(redissonClient, groupQueue);
        mockTasks(redissonClient);
        mockAttempts(redissonClient);
        RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient);
        AtomicReference<ReceiptTask> consumedTask = new AtomicReference<>();

        consume(store, ReceiptType.PRIVATE_MESSAGE_SEND, "ack:1", consumedTask::set);

        assertThat(consumedTask).hasNullValue();
        verify(privateQueue).poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        verify(groupQueue, never()).poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Test
    void initOnlyPollsAssignedShardForReceiptType() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> firstShardQueue = mockQueue(redissonClient, ReceiptType.PRIVATE_MESSAGE_SEND, receiptIdForShard(0));
        RBlockingDeque<String> secondShardQueue = mockQueue(redissonClient, ReceiptType.PRIVATE_MESSAGE_SEND, receiptIdForShard(1));
        mockDelayedQueue(redissonClient, firstShardQueue);
        mockDelayedQueue(redissonClient, secondShardQueue);
        mockTasks(redissonClient);
        mockAttempts(redissonClient);
        RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient);

        consume(store, ReceiptType.PRIVATE_MESSAGE_SEND, receiptIdForShard(1), ignored -> {
        });

        verify(firstShardQueue, never()).poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        verify(secondShardQueue).poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Test
    void initAcknowledgesAndDropsMessageWhenRetryLimitExceeded() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> receiptQueue = mockQueue(redissonClient, ReceiptType.PRIVATE_MESSAGE_SEND, "ack:1");
        mockDelayedQueue(redissonClient, receiptQueue);
        RMapCache<String, String> receiptTasks = mockTasks(redissonClient);
        RMapCache<String, Integer> receiptAttempts = mockAttempts(redissonClient);
        ReceiptTask task = task();
        when(receiptAttempts.addAndGet("ack:1", 1)).thenReturn(4);
        String taskJson = JsonUtils.toJson(task);
        poll(receiptQueue, "ack:1");
        when(receiptTasks.get("ack:1")).thenReturn(taskJson);
        RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient);
        AtomicReference<ReceiptTask> consumedTask = new AtomicReference<>();

        consume(store, ReceiptType.PRIVATE_MESSAGE_SEND, "ack:1", consumedTask::set);

        assertThat(consumedTask).hasNullValue();
        verify(receiptTasks).fastRemove("ack:1");
        verify(receiptAttempts).fastRemove("ack:1");
    }

    @Test
    void initSkipsTaskWhenTaskHasBeenConfirmed() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> receiptQueue = mockQueue(redissonClient, ReceiptType.PRIVATE_MESSAGE_SEND, "ack:1");
        mockDelayedQueue(redissonClient, receiptQueue);
        RMapCache<String, String> receiptTasks = mockTasks(redissonClient);
        RMapCache<String, Integer> receiptAttempts = mockAttempts(redissonClient);
        poll(receiptQueue, "ack:1");
        when(receiptTasks.get("ack:1")).thenReturn(null);
        RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient);
        AtomicReference<ReceiptTask> consumedTask = new AtomicReference<>();

        consume(store, ReceiptType.PRIVATE_MESSAGE_SEND, "ack:1", consumedTask::set);

        assertThat(consumedTask).hasNullValue();
        verify(receiptTasks, never()).fastRemove("ack:1");
        verify(receiptAttempts).fastRemove("ack:1");
    }

    @Test
    void initDoesNothingWhenQueueHasNoMessage() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> receiptQueue = mockQueue(redissonClient, ReceiptType.PRIVATE_MESSAGE_SEND, "ack:1");
        mockDelayedQueue(redissonClient, receiptQueue);
        mockTasks(redissonClient);
        mockAttempts(redissonClient);
        RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient);
        AtomicReference<ReceiptTask> consumedTask = new AtomicReference<>();

        consume(store, ReceiptType.PRIVATE_MESSAGE_SEND, "ack:1", consumedTask::set);

        assertThat(consumedTask).hasNullValue();
        verify(receiptQueue).poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Test
    void startConfirmingIgnoresDuplicateStarts() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        mockQueue(redissonClient, ReceiptType.PRIVATE_MESSAGE_SEND, "ack:1");
        mockTasks(redissonClient);
        mockAttempts(redissonClient);
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient, executor);

        store.startConfirming(ignored -> {
        });
        store.startConfirming(ignored -> {
        });

        verify(executor, times(ReceiptType.values().length * 16)).scheduleWithFixedDelay(
                any(Runnable.class), eq(1L), eq(1L), eq(TimeUnit.SECONDS));
    }

    @Test
    void consumeBatchDrainsUpToBatchLimit() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        String firstReceiptId = receiptIdForShard(0);
        RBlockingDeque<String> receiptQueue = mockQueue(redissonClient, ReceiptType.PRIVATE_MESSAGE_SEND, firstReceiptId);
        RDelayedQueue<String> delayedReceiptQueue = mockDelayedQueue(redissonClient, receiptQueue);
        RMapCache<String, String> receiptTasks = mockTasks(redissonClient);
        RMapCache<String, Integer> receiptAttempts = mockAttempts(redissonClient);
        RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient);
        ReceiptTask task = task(firstReceiptId, ReceiptType.PRIVATE_MESSAGE_SEND);
        String taskJson = JsonUtils.toJson(task);
        try {
            when(receiptQueue.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                    .thenReturn(firstReceiptId, firstReceiptId, firstReceiptId, firstReceiptId, firstReceiptId,
                            firstReceiptId, firstReceiptId, firstReceiptId, firstReceiptId, firstReceiptId, null);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        when(receiptTasks.get(firstReceiptId)).thenReturn(taskJson);
        when(receiptAttempts.addAndGet(firstReceiptId, 1)).thenReturn(1);

        consumeBatch(store, ReceiptType.PRIVATE_MESSAGE_SEND, firstReceiptId, ignored -> {
        });

        verify(receiptAttempts, times(10)).addAndGet(firstReceiptId, 1);
        verify(delayedReceiptQueue, times(10)).offer(firstReceiptId, 2000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void initRequeuesTaskWhenConsumerFails() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> receiptQueue = mockQueue(redissonClient, ReceiptType.PRIVATE_MESSAGE_SEND, "ack:1");
        RDelayedQueue<String> delayedReceiptQueue = mockDelayedQueue(redissonClient, receiptQueue);
        RMapCache<String, String> receiptTasks = mockTasks(redissonClient);
        RMapCache<String, Integer> receiptAttempts = mockAttempts(redissonClient);
        when(receiptAttempts.addAndGet("ack:1", 1)).thenReturn(1);
        ReceiptTask task = task();
        String taskJson = JsonUtils.toJson(task);
        poll(receiptQueue, "ack:1");
        when(receiptTasks.get("ack:1")).thenReturn(taskJson);
        RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient);

        try {
            consume(store, ReceiptType.PRIVATE_MESSAGE_SEND, "ack:1", ignored -> {
                throw new IllegalStateException("consume failed");
            });
        } catch (IllegalStateException ignored) {
        }

        verify(receiptQueue).poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        verify(receiptTasks).expireEntry(eq("ack:1"), any(), any());
        verify(delayedReceiptQueue).offer("ack:1", 2000L, TimeUnit.MILLISECONDS);
        verify(receiptTasks, never()).fastRemove("ack:1");
    }

    @Test
    void initDoesNotRequeueTaskWhenTaskHasBeenRemovedAfterConsumerFails() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> receiptQueue = mockQueue(redissonClient, ReceiptType.PRIVATE_MESSAGE_SEND, "ack:1");
        RDelayedQueue<String> delayedReceiptQueue = mockDelayedQueue(redissonClient, receiptQueue);
        RMapCache<String, String> receiptTasks = mockTasks(redissonClient);
        RMapCache<String, Integer> receiptAttempts = mockAttempts(redissonClient);
        when(receiptAttempts.addAndGet("ack:1", 1)).thenReturn(1);
        ReceiptTask task = task();
        String taskJson = JsonUtils.toJson(task);
        poll(receiptQueue, "ack:1");
        when(receiptTasks.get("ack:1")).thenReturn(taskJson);
        when(receiptTasks.get("ack:1")).thenReturn(taskJson, null);
        RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient);

        try {
            consume(store, ReceiptType.PRIVATE_MESSAGE_SEND, "ack:1", ignored -> {
                throw new IllegalStateException("consume failed");
            });
        } catch (IllegalStateException ignored) {
        }

        verify(delayedReceiptQueue, never()).offer("ack:1", 2000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void confirmRemovesReceiptTaskByReceiptId() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> receiptQueue = mockQueue(redissonClient, ReceiptType.PRIVATE_MESSAGE_SEND, "ack:1");
        RDelayedQueue<String> delayedReceiptQueue = mockDelayedQueue(redissonClient, receiptQueue);
        RMapCache<String, String> receiptTasks = mockTasks(redissonClient);
        RMapCache<String, Integer> receiptAttempts = mockAttempts(redissonClient);
        when(receiptTasks.get("ack:1")).thenReturn(JsonUtils.toJson(task()));
        RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient);

        store.confirm("ack:1");

        verify(receiptTasks).fastRemove("ack:1");
        verify(delayedReceiptQueue).remove("ack:1");
        verify(receiptQueue).remove("ack:1");
        verify(receiptAttempts).fastRemove("ack:1");
    }

    @SuppressWarnings("unchecked")
    private RBlockingDeque<String> mockQueue(RedissonClient redissonClient, ReceiptType receiptType, String receiptId) {
        if (!org.mockito.Mockito.mockingDetails(redissonClient).getStubbings().isEmpty()) {
            RBlockingDeque<String> receiptQueue = mock(RBlockingDeque.class);
            when(redissonClient.<String>getBlockingDeque(queueName(receiptType, receiptId), StringCodec.INSTANCE))
                    .thenReturn(receiptQueue);
            return receiptQueue;
        }
        Map<String, RBlockingDeque<String>> queues = new HashMap<>();
        Map<RBlockingDeque<String>, RDelayedQueue<String>> delayedQueues = new HashMap<>();
        when(redissonClient.<String>getBlockingDeque(any(), eq(StringCodec.INSTANCE)))
                .thenAnswer(invocation -> queues.computeIfAbsent(invocation.getArgument(0), ignored -> mock(RBlockingDeque.class)));
        when(redissonClient.<String>getDelayedQueue(any()))
                .thenAnswer(invocation -> delayedQueues.computeIfAbsent(invocation.getArgument(0), ignored -> mock(RDelayedQueue.class)));
        return queues.computeIfAbsent(queueName(receiptType, receiptId), ignored -> mock(RBlockingDeque.class));
    }

    private String queueName(ReceiptType receiptType, String receiptId) {
        return "im:message:receipt:delay:" + queueIdx(receiptType) + ":" + shard(receiptId);
    }

    private int shard(String receiptId) {
        return Math.floorMod(receiptId.hashCode(), 16);
    }

    private int queueIdx(ReceiptType receiptType) {
        return receiptType.ordinal() % ReceiptType.values().length;
    }

    /**
     * startConfirming() only registers scheduled workers. Call the single-queue consume path
     * directly so these tests stay deterministic without waiting for background threads.
     */
    private boolean consume(RedisImMessageConfirmableStore store,
                            ReceiptType receiptType,
                            String receiptId,
                            java.util.function.Consumer<ReceiptTask> consumer) {
        try {
            Method consume = RedisImMessageConfirmableStore.class.getDeclaredMethod(
                    "consume", int.class, int.class, java.util.function.Consumer.class);
            consume.setAccessible(true);
            return (boolean) consume.invoke(store, queueIdx(receiptType), shard(receiptId), consumer);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(e);
        }
    }

    private void consumeBatch(RedisImMessageConfirmableStore store,
                              ReceiptType receiptType,
                              String receiptId,
                              java.util.function.Consumer<ReceiptTask> consumer) {
        try {
            Method consume = RedisImMessageConfirmableStore.class.getDeclaredMethod(
                    "consumeBatch", int.class, int.class, java.util.function.Consumer.class);
            consume.setAccessible(true);
            consume.invoke(store, queueIdx(receiptType), shard(receiptId), consumer);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(e);
        }
    }

    private String receiptIdForShard(int shard) {
        int i = 0;
        while (true) {
            String receiptId = "ack:" + i;
            if (shard(receiptId) == shard) {
                return receiptId;
            }
            i++;
        }
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    private RDelayedQueue<String> mockDelayedQueue(RedissonClient redissonClient, RBlockingDeque<String> receiptQueue) {
        RDelayedQueue<String> delayedReceiptQueue = mock(RDelayedQueue.class);
        when(redissonClient.<String>getDelayedQueue(receiptQueue)).thenReturn(delayedReceiptQueue);
        return delayedReceiptQueue;
    }

    private void poll(RBlockingDeque<String> receiptQueue, String receiptId) {
        try {
            when(receiptQueue.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).thenReturn(receiptId);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private RMapCache<String, String> mockTasks(RedissonClient redissonClient) {
        RMapCache<String, String> receiptTasks = mock(RMapCache.class);
        when(redissonClient.<String, String>getMapCache("im:message:receipt:delay:tasks", StringCodec.INSTANCE))
                .thenReturn(receiptTasks);
        return receiptTasks;
    }

    @SuppressWarnings("unchecked")
    private RMapCache<String, Integer> mockAttempts(RedissonClient redissonClient) {
        RMapCache<String, Integer> receiptAttempts = mock(RMapCache.class);
        when(redissonClient.<String, Integer>getMapCache("im:message:receipt:delay:attempts", StringCodec.INSTANCE))
                .thenReturn(receiptAttempts);
        return receiptAttempts;
    }

    private ReceiptTask task() {
        return task("ack:1", ReceiptType.PRIVATE_MESSAGE_SEND);
    }

    private ReceiptTask task(String receiptId, ReceiptType receiptType) {
        return ReceiptTask.builder()
                .receiptId(receiptId)
                .receiptType(receiptType)
                .notification(new TestNotification("hello"))
                .delayMillis(2000L)
                .build();
    }

    private static class TestNotification {
        private String value;

        public TestNotification() {
        }

        public TestNotification(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
