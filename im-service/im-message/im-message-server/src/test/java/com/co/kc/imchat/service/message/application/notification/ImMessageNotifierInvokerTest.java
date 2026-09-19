package com.co.kc.imchat.service.message.application.notification;

import com.co.kc.imchat.service.message.application.notification.ImMessageNotifier;
import com.co.kc.imchat.service.message.application.notification.ImMessageNotifierFactory;
import com.co.kc.imchat.service.message.application.notification.ImMessageNotifierInvoker;
import com.co.kc.imchat.service.message.application.notification.confirmable.ImMessageConfirmable;
import com.co.kc.imchat.service.message.application.notification.confirmable.ImMessageConfirmableService;
import com.co.kc.imchat.service.message.application.notification.confirmable.ImMessageConfirmableStore;
import com.co.kc.imchat.service.message.application.notification.task.ReceiptTask;
import com.co.kc.imchat.service.message.application.notification.task.ReceiptType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImMessageNotifierInvokerTest {

    @Test
    void invokeSchedulesConfirmableTaskAndNotifies() throws InterruptedException {
        RecordingNotifier notifier = new RecordingNotifier();
        RecordingConfirmableStore confirmableStore = new RecordingConfirmableStore();
        ImMessageNotifierInvoker invoker = newInvoker(notifier, confirmableStore);

        TestNotification notification = new TestNotification("hello");
        invoker.invoke(notification);

        assertThat(notifier.awaitNotification()).isTrue();
        assertThat(notifier.notifiedNotification).isSameAs(notification);
        assertThat(confirmableStore.offeredTask).isNotNull();
        assertThat(confirmableStore.offeredTask.getReceiptType()).isEqualTo(ReceiptType.PRIVATE_MESSAGE_SEND);
        assertThat(confirmableStore.offeredTask.getNotification()).isSameAs(notification);
        assertThat(confirmableStore.offeredTask.getReceiptId()).isEqualTo("ack:hello");
        assertThat(confirmableStore.offeredTask.getDelayMillis()).isEqualTo(2000L);
    }

    @Test
    void invokeSkipsSchedulingWhenConfirmableDisablesRetry() {
        NoRetryNotifier notifier = new NoRetryNotifier();
        RecordingConfirmableStore confirmableStore = new RecordingConfirmableStore();
        ImMessageNotifierInvoker invoker = newInvoker(notifier, confirmableStore);

        TestNotification notification = new TestNotification("hello");
        invoker.invoke(notification);

        assertThat(notifier.notifiedNotification).isSameAs(notification);
        assertThat(confirmableStore.offeredTask).isNull();
    }

    @Test
    void invokeIsolatesInitialFailureAfterSchedulingConfirmableTask() throws InterruptedException {
        FailingNotifier notifier = new FailingNotifier();
        RecordingConfirmableStore confirmableStore = new RecordingConfirmableStore();
        ImMessageNotifierInvoker invoker = newInvoker(notifier, confirmableStore);

        assertThatCode(() -> invoker.invoke(new TestNotification("hello")))
                .doesNotThrowAnyException();
        assertThat(confirmableStore.offeredTask).isNotNull();
        assertThat(notifier.awaitFailure()).isTrue();
    }

    @Test
    void invokeDispatchesConfirmableNotificationOnVirtualThread() throws InterruptedException {
        RecordingNotifier notifier = new RecordingNotifier();
        RecordingConfirmableStore confirmableStore = new RecordingConfirmableStore();
        ImMessageNotifierInvoker invoker = newInvoker(notifier, confirmableStore);
        TestNotification notification = new TestNotification("hello");

        invoker.invoke(notification);

        assertThat(confirmableStore.offeredTask).isNotNull();
        assertThat(notifier.awaitNotification()).isTrue();
        assertThat(notifier.notifiedNotification).isSameAs(notification);
        assertThat(notifier.notificationThread).isNotSameAs(Thread.currentThread());
        assertThat(notifier.notificationThread.isVirtual()).isTrue();
    }

    @Test
    void invokePropagatesFailureWhenRetryIsDisabled() {
        NoRetryFailingNotifier notifier = new NoRetryFailingNotifier();
        ImMessageNotifierInvoker invoker = newInvoker(notifier, new RecordingConfirmableStore());

        assertThatThrownBy(() -> invoker.invoke(new TestNotification("hello")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("broker unavailable");
    }

    private ImMessageNotifierInvoker newInvoker(
            ImMessageNotifier<TestNotification> notifier,
            RecordingConfirmableStore confirmableStore) {
        ImMessageConfirmableService confirmableService = new ImMessageConfirmableService(
                new ImMessageNotifierFactory(Collections.singletonList(notifier)),
                confirmableStore);
        return new ImMessageNotifierInvoker(
                new ImMessageNotifierFactory(Collections.singletonList(notifier)),
                confirmableService);
    }

    private static class RecordingNotifier implements ImMessageNotifier<TestNotification>, ImMessageConfirmable<TestNotification> {
        private final CountDownLatch notificationLatch = new CountDownLatch(1);
        protected volatile TestNotification notifiedNotification;
        private volatile Thread notificationThread;

        @Override
        public void notify(TestNotification notification) {
            notifiedNotification = notification;
            notificationThread = Thread.currentThread();
            notificationLatch.countDown();
        }

        boolean awaitNotification() throws InterruptedException {
            return notificationLatch.await(1, TimeUnit.SECONDS);
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

    private static class NoRetryNotifier extends RecordingNotifier {
        @Override
        public long delayMillis() {
            return 0L;
        }
    }

    private static class FailingNotifier extends RecordingNotifier {
        private final CountDownLatch failureLatch = new CountDownLatch(1);

        @Override
        public void notify(TestNotification notification) {
            failureLatch.countDown();
            throw new IllegalStateException("broker unavailable");
        }

        boolean awaitFailure() throws InterruptedException {
            return failureLatch.await(1, TimeUnit.SECONDS);
        }
    }

    private static class NoRetryFailingNotifier extends FailingNotifier {
        @Override
        public long delayMillis() {
            return 0L;
        }
    }

    private static class RecordingConfirmableStore implements ImMessageConfirmableStore {
        private ReceiptTask offeredTask;

        @Override
        public void startConfirming(Consumer<ReceiptTask> consumer) {
        }

        @Override
        public void stopConfirming() {
        }

        @Override
        public void offer(ReceiptTask message) {
            this.offeredTask = message;
        }

        @Override
        public void confirm(String receiptId) {
        }

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TestNotification {
        private String value;
    }
}
