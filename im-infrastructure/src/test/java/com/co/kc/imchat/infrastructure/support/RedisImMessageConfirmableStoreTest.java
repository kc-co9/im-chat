package com.co.kc.imchat.infrastructure.support;

import com.co.kc.imchat.application.support.notifier.task.ReceiptTask;
import com.co.kc.imchat.application.support.notifier.task.ReceiptType;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.infrastructure.support.notifier.confirmation.RedisImMessageConfirmableStore;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisImMessageConfirmableStoreTest {

    @Test
    void offerAddsDelayedReceiptIdAndStoresTask() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RDelayedQueue<String> delayedReceiptQueue = mockDelayedQueue(redissonClient, mockQueue(redissonClient));
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
    void offerStoresTaskWithoutDeduplication() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> receiptQueue = mockQueue(redissonClient);
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
        verifyNoInteractions(receiptQueue);
    }

    @Test
    void consumeAcknowledgesQueueMessageAfterConsumerSucceeds() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> receiptQueue = mockQueue(redissonClient);
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

        store.consume(consumedTask::set);

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
    void consumeAcknowledgesAndDropsMessageWhenRetryLimitExceeded() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> receiptQueue = mockQueue(redissonClient);
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

        store.consume(consumedTask::set);

        assertThat(consumedTask).hasNullValue();
        verify(receiptTasks).fastRemove("ack:1");
        verify(receiptAttempts).fastRemove("ack:1");
    }

    @Test
    void consumeSkipsTaskWhenTaskHasBeenConfirmed() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> receiptQueue = mockQueue(redissonClient);
        mockDelayedQueue(redissonClient, receiptQueue);
        RMapCache<String, String> receiptTasks = mockTasks(redissonClient);
        RMapCache<String, Integer> receiptAttempts = mockAttempts(redissonClient);
        poll(receiptQueue, "ack:1");
        when(receiptTasks.get("ack:1")).thenReturn(null);
        RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient);
        AtomicReference<ReceiptTask> consumedTask = new AtomicReference<>();

        store.consume(consumedTask::set);

        assertThat(consumedTask).hasNullValue();
        verify(receiptTasks, never()).fastRemove("ack:1");
        verify(receiptAttempts).fastRemove("ack:1");
    }

    @Test
    void consumeDoesNothingWhenQueueHasNoMessage() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> receiptQueue = mockQueue(redissonClient);
        mockDelayedQueue(redissonClient, receiptQueue);
        mockTasks(redissonClient);
        mockAttempts(redissonClient);
        RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient);
        AtomicReference<ReceiptTask> consumedTask = new AtomicReference<>();

        store.consume(consumedTask::set);

        assertThat(consumedTask).hasNullValue();
        verify(receiptQueue).poll(500L, TimeUnit.MILLISECONDS);
    }

    @Test
    void consumeRequeuesTaskWhenConsumerFails() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> receiptQueue = mockQueue(redissonClient);
        RDelayedQueue<String> delayedReceiptQueue = mockDelayedQueue(redissonClient, receiptQueue);
        RMapCache<String, String> receiptTasks = mockTasks(redissonClient);
        RMapCache<String, Integer> receiptAttempts = mockAttempts(redissonClient);
        when(receiptAttempts.addAndGet("ack:1", 1)).thenReturn(1);
        ReceiptTask task = task();
        String taskJson = JsonUtils.toJson(task);
        poll(receiptQueue, "ack:1");
        when(receiptTasks.get("ack:1")).thenReturn(taskJson);
        when(receiptTasks.containsKey("ack:1")).thenReturn(true);
        RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient);

        try {
            store.consume(ignored -> {
                throw new IllegalStateException("consume failed");
            });
        } catch (IllegalStateException ignored) {
        }

        verify(receiptQueue).poll(500L, TimeUnit.MILLISECONDS);
        verify(receiptTasks).expireEntry(eq("ack:1"), any(), any());
        verify(delayedReceiptQueue).offer("ack:1", 2000L, TimeUnit.MILLISECONDS);
        verify(receiptTasks, never()).fastRemove("ack:1");
    }

    @Test
    void consumeDoesNotRequeueTaskWhenTaskHasBeenRemovedAfterConsumerFails() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> receiptQueue = mockQueue(redissonClient);
        RDelayedQueue<String> delayedReceiptQueue = mockDelayedQueue(redissonClient, receiptQueue);
        RMapCache<String, String> receiptTasks = mockTasks(redissonClient);
        RMapCache<String, Integer> receiptAttempts = mockAttempts(redissonClient);
        when(receiptAttempts.addAndGet("ack:1", 1)).thenReturn(1);
        ReceiptTask task = task();
        String taskJson = JsonUtils.toJson(task);
        poll(receiptQueue, "ack:1");
        when(receiptTasks.get("ack:1")).thenReturn(taskJson);
        when(receiptTasks.containsKey("ack:1")).thenReturn(false);
        RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient);

        try {
            store.consume(ignored -> {
                throw new IllegalStateException("consume failed");
            });
        } catch (IllegalStateException ignored) {
        }

        verify(delayedReceiptQueue, never()).offer("ack:1", 2000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void confirmRemovesReceiptTaskByReceiptId() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingDeque<String> receiptQueue = mockQueue(redissonClient);
        RDelayedQueue<String> delayedReceiptQueue = mockDelayedQueue(redissonClient, receiptQueue);
        RMapCache<String, String> receiptTasks = mockTasks(redissonClient);
        RMapCache<String, Integer> receiptAttempts = mockAttempts(redissonClient);
        RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient);

        store.confirm("ack:1");

        verify(receiptTasks).fastRemove("ack:1");
        verify(delayedReceiptQueue).remove("ack:1");
        verify(receiptQueue).remove("ack:1");
        verify(receiptAttempts).fastRemove("ack:1");
    }

    @SuppressWarnings("unchecked")
    private RBlockingDeque<String> mockQueue(RedissonClient redissonClient) {
        RBlockingDeque<String> receiptQueue = mock(RBlockingDeque.class);
        when(redissonClient.<String>getBlockingDeque("im:message:receipt:delay", StringCodec.INSTANCE))
                .thenReturn(receiptQueue);
        return receiptQueue;
    }

    @SuppressWarnings("unchecked")
    private RDelayedQueue<String> mockDelayedQueue(RedissonClient redissonClient, RBlockingDeque<String> receiptQueue) {
        RDelayedQueue<String> delayedReceiptQueue = mock(RDelayedQueue.class);
        when(redissonClient.<String>getDelayedQueue(receiptQueue)).thenReturn(delayedReceiptQueue);
        return delayedReceiptQueue;
    }

    private void poll(RBlockingDeque<String> receiptQueue, String receiptId) {
        try {
            when(receiptQueue.poll(500L, TimeUnit.MILLISECONDS)).thenReturn(receiptId);
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
        return ReceiptTask.builder()
                .receiptId("ack:1")
                .receiptType(ReceiptType.PRIVATE_MESSAGE_SEND)
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
