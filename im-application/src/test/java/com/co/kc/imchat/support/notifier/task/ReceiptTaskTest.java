package com.co.kc.imchat.support.notifier.task;

import com.co.kc.imchat.application.support.notifier.task.ReceiptTask;
import com.co.kc.imchat.application.support.notifier.task.ReceiptType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptTaskTest {

    @Test
    void builderRejectsMissingRequiredFields() {
        assertThatThrownBy(() -> ReceiptTask.builder()
                .receiptType(ReceiptType.PRIVATE_MESSAGE_SEND)
                .notification(new TestNotification())
                .delayMillis(2000L)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("receiptId不能为空");
    }

    @Test
    void builderRejectsNonPositiveDelayMillis() {
        assertThatThrownBy(() -> ReceiptTask.builder()
                .receiptId("ack:1")
                .receiptType(ReceiptType.PRIVATE_MESSAGE_SEND)
                .notification(new TestNotification())
                .delayMillis(0L)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("delayMillis必须大于0");
    }

    private static class TestNotification {
    }
}
