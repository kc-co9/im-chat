package com.co.kc.imchat.support.notifier;

import com.co.kc.imchat.support.notifier.task.NotifierTask;
import com.co.kc.imchat.support.notifier.task.NotifierTaskType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ImMessageNotifierInvokerTest {

    @Test
    void invokeSchedulesConfirmableTaskAndNotifies() {
        RecordingNotifier notifier = new RecordingNotifier();
        RecordingScheduler scheduler = new RecordingScheduler();
        ImMessageNotifierInvoker invoker = newInvoker(notifier, scheduler);

        TestCommand command = new TestCommand("hello");
        invoker.invoke(command);

        assertThat(notifier.notifiedCommand).isSameAs(command);
        assertThat(scheduler.scheduledTask).isNotNull();
        assertThat(scheduler.scheduledTask.getType()).isEqualTo(NotifierTaskType.PRIVATE_MESSAGE_SEND);
        assertThat(scheduler.scheduledTask.getCommand()).contains("hello");
    }

    @Test
    void retryNotifiesWithoutSchedulingAgain() {
        RecordingNotifier notifier = new RecordingNotifier();
        RecordingScheduler scheduler = new RecordingScheduler();
        ImMessageNotifierInvoker invoker = newInvoker(notifier, scheduler);

        invoker.retry(NotifierTaskType.PRIVATE_MESSAGE_SEND, new TestCommand("retry"));

        assertThat(notifier.notifiedCommand.getValue()).isEqualTo("retry");
        assertThat(scheduler.scheduleCount.get()).isEqualTo(0);
    }

    @Test
    void retryDeserializesJsonCommandBeforeNotifying() {
        RecordingNotifier notifier = new RecordingNotifier();
        RecordingScheduler scheduler = new RecordingScheduler();
        ImMessageNotifierInvoker invoker = newInvoker(notifier, scheduler);

        invoker.retry(NotifierTaskType.PRIVATE_MESSAGE_SEND, "{\"value\":\"retry\"}");

        assertThat(notifier.notifiedCommand.getValue()).isEqualTo("retry");
        assertThat(scheduler.scheduleCount.get()).isEqualTo(0);
    }

    private ImMessageNotifierInvoker newInvoker(RecordingNotifier notifier, RecordingScheduler scheduler) {
        return new ImMessageNotifierInvoker(
                new ImMessageNotifierFactory(Collections.singletonList(notifier)),
                scheduler);
    }

    private static class RecordingNotifier implements ImMessageNotifier<TestCommand>, ImMessageConfirmable {
        private TestCommand notifiedCommand;

        @Override
        public void notify(TestCommand command) {
            notifiedCommand = command;
        }

        @Override
        public NotifierTaskType task() {
            return NotifierTaskType.PRIVATE_MESSAGE_SEND;
        }
    }

    private static class RecordingScheduler implements ImMessageConfirmableScheduler {
        private final AtomicInteger scheduleCount = new AtomicInteger();
        private NotifierTask scheduledTask;

        @Override
        public void schedule(NotifierTask task) {
            scheduleCount.incrementAndGet();
            scheduledTask = task;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TestCommand {
        private String value;
    }
}
