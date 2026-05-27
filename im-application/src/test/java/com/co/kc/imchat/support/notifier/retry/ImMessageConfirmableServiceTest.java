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
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

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
    void initStartsConfirmingInStore() {
        RecordingConfirmableStore confirmableStore = new RecordingConfirmableStore();
        ImMessageConfirmableService service = newService(new RecordingNotifier(), confirmableStore);

        service.init();

        assertThat(confirmableStore.consumer).isNotNull();
    }

    @Test
    void destroyStopsConfirmingInStore() {
        RecordingConfirmableStore confirmableStore = new RecordingConfirmableStore();
        ImMessageConfirmableService service = newService(new RecordingNotifier(), confirmableStore);

        service.destroy();

        assertThat(confirmableStore.stopped).isTrue();
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

    private ImMessageConfirmableService newService(
            RecordingNotifier notifier,
            RecordingConfirmableStore confirmableStore) {
        return new ImMessageConfirmableService(
                new ImMessageNotifierFactory(Collections.singletonList(notifier)),
                confirmableStore);
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

    private static class RecordingConfirmableStore implements ImMessageConfirmableStore {
        private ReceiptTask offeredTask;
        private Consumer<ReceiptTask> consumer;
        private boolean stopped;
        private String confirmedReceiptId;

        @Override
        public void startConfirming(Consumer<ReceiptTask> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void stopConfirming() {
            this.stopped = true;
        }

        @Override
        public void offer(ReceiptTask message) {
            this.offeredTask = message;
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
