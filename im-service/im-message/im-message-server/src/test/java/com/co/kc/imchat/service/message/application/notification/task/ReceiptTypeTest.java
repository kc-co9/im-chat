package com.co.kc.imchat.service.message.application.notification.task;

import com.co.kc.imchat.service.message.application.notification.task.ReceiptType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptTypeTest {

    @Test
    void receiptIdUsesTaskReceiverChatAndMessage() {
        assertThat(ReceiptType.PRIVATE_MESSAGE_SEND.receiptId(2L, 102L, 900L))
                .isEqualTo("PRIVATE_MESSAGE_SEND:2:102:900");
        assertThat(ReceiptType.GROUP_MESSAGE_REVOKE.receiptId(3L, 203L, 901L))
                .isEqualTo("GROUP_MESSAGE_REVOKE:3:203:901");
    }
}
