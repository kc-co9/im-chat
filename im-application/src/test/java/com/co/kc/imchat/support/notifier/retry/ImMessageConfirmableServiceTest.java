package com.co.kc.imchat.support.notifier.retry;

import com.co.kc.imchat.application.support.notifier.ImMessageNotifier;
import com.co.kc.imchat.application.support.notifier.ImMessageNotifierFactory;
import com.co.kc.imchat.application.support.notifier.confirmable.ImMessageConfirmable;
import com.co.kc.imchat.application.support.notifier.confirmable.ImMessageConfirmableService;
import com.co.kc.imchat.application.support.notifier.confirmable.ImMessageConfirmableStore;
import com.co.kc.imchat.application.support.notifier.task.ReceiptTask;
import com.co.kc.imchat.application.support.notifier.task.ReceiptType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ImMessageConfirmableServiceTest {
    static {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ImMessageConfirmableService.class))
                .setLevel(ch.qos.logback.classic.Level.OFF);
    }

    @Test
    void schedulePushesInitialReceiptTask() {
        RecordingNotifier notifier = new RecordingNotifier();
        RecordingConfirmableStore confirmableStore = new RecordingConfirmableStore();
        ImMessageConfirmableService service = newService(notifier, confirmableStore);

        service.schedule(notifier, new TestNotification("hello"));

        assertThat(confirmableStore.offeredTask).isNotNull();
        assertThat(confirmableStore.offeredTask.getReceiptType()).isEqualTo(ReceiptType.PRIVATE_MESSAGE_SEND);
        assertThat(confirmableStore.offeredTask.getReceiptId()).isEqualTo("ack:hello");
        assertThat(confirmableStore.offeredTask.getNotification()).isEqualTo(new TestNotification("hello"));
        assertThat(confirmableStore.offeredTask.getDelayMillis()).isEqualTo(2000L);
    }

    @Test
    void initSchedulesConfirmableTaskConsumerWithInjectedExecutor() {
        RecordingConfirmableStore confirmableStore = new RecordingConfirmableStore();
        ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
        ImMessageConfirmableService service = new ImMessageConfirmableService(
                new ImMessageNotifierFactory(Collections.singletonList(new RecordingNotifier())),
                confirmableStore,
                scheduledExecutorService);

        service.init();

        verify(scheduledExecutorService).scheduleWithFixedDelay(
                any(Runnable.class), org.mockito.Mockito.eq(1L), org.mockito.Mockito.eq(1L), org.mockito.Mockito.eq(TimeUnit.SECONDS));
    }

    @Test
    void scheduleThrowsWhenStorePushFails() {
        RecordingNotifier notifier = new RecordingNotifier();
        FailingConfirmableStore confirmableStore = new FailingConfirmableStore();
        ImMessageConfirmableService service = newService(notifier, confirmableStore);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.schedule(notifier, new TestNotification("hello")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("push failed");
    }

    @Test
    void confirmDelegatesToStore() {
        RecordingConfirmableStore confirmableStore = new RecordingConfirmableStore();
        ImMessageConfirmableService service = newService(new RecordingNotifier(), confirmableStore);

        service.confirm("ack:hello");

        assertThat(confirmableStore.confirmedReceiptId).isEqualTo("ack:hello");
    }

    @Test
    void consumeRedeliversPoppedTaskAndConvertsNotificationMap() throws Exception {
        RecordingNotifier notifier = new RecordingNotifier();
        RecordingConfirmableStore confirmableStore = new RecordingConfirmableStore();
        confirmableStore.consumedTask = newTask(Map.of("value", "retry"));
        ImMessageConfirmableService service = newService(notifier, confirmableStore);

        invokeConsume(service);

        assertThat(notifier.notifiedNotification).isEqualTo(new TestNotification("retry"));
        assertThat(confirmableStore.offeredTask).isNull();
    }

    @Test
    void consumeReturnsWhenStoreHasNoTask() throws Exception {
        RecordingNotifier notifier = new RecordingNotifier();
        RecordingConfirmableStore confirmableStore = new RecordingConfirmableStore();
        ImMessageConfirmableService service = newService(notifier, confirmableStore);

        invokeConsume(service);

        assertThat(notifier.notifiedNotification).isNull();
        assertThat(confirmableStore.offeredTask).isNull();
    }

    @Test
    void consumeSwallowsProcessingExceptionSoScheduledWorkerCanContinue() throws Exception {
        ThrowingNotifier notifier = new ThrowingNotifier();
        RecordingConfirmableStore confirmableStore = new RecordingConfirmableStore();
        confirmableStore.consumedTask = newTask(new TestNotification("retry"));
        ImMessageConfirmableService service = newService(notifier, confirmableStore);

        invokeConsume(service);

        assertThat(notifier.calls).isEqualTo(1);
    }

    private ImMessageConfirmableService newService(
            RecordingNotifier notifier,
            RecordingConfirmableStore confirmableStore) {
        return new ImMessageConfirmableService(
                new ImMessageNotifierFactory(Collections.singletonList(notifier)),
                confirmableStore,
                mock(ScheduledExecutorService.class));
    }

    private void invokeConsume(ImMessageConfirmableService service) throws Exception {
        var consume = ImMessageConfirmableService.class.getDeclaredMethod("consumeSafely");
        consume.setAccessible(true);
        consume.invoke(service);
    }

    private ReceiptTask newTask(Object notification) {
        return ReceiptTask.builder()
                .receiptType(ReceiptType.PRIVATE_MESSAGE_SEND)
                .notification(notification)
                .receiptId("ack:retry")
                .delayMillis(2000L)
                .build();
    }

    private static class RecordingNotifier implements ImMessageNotifier<TestNotification>, ImMessageConfirmable<TestNotification> {
        private TestNotification notifiedNotification;

        @Override
        public void notify(TestNotification notification) {
            notifiedNotification = notification;
        }

        @Override
        public ReceiptType receiptType() {
            return ReceiptType.PRIVATE_MESSAGE_SEND;
        }

        @Override
        public String receiptId(TestNotification notification) {
            return "ack:" + notification.getValue();
        }
    }

    private static class ThrowingNotifier extends RecordingNotifier {
        private int calls;

        @Override
        public void notify(TestNotification notification) {
            calls++;
            throw new IllegalStateException("notify failed");
        }
    }

    private static class RecordingConfirmableStore implements ImMessageConfirmableStore {
        private ReceiptTask offeredTask;
        private ReceiptTask consumedTask;
        private String confirmedReceiptId;

        @Override
        public void offer(ReceiptTask message) {
            this.offeredTask = message;
        }

        @Override
        public void consume(Consumer<ReceiptTask> consumer) {
            if (consumedTask != null) {
                consumer.accept(consumedTask);
            }
        }

        @Override
        public void confirm(String receiptId) {
            this.confirmedReceiptId = receiptId;
        }
    }

    private static class FailingConfirmableStore extends RecordingConfirmableStore {
        @Override
        public void offer(ReceiptTask message) {
            throw new IllegalStateException("push failed");
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TestNotification {
        private String value;
    }
}
