package com.co.kc.imchat.service.message.transformer;

import com.co.kc.imchat.service.message.model.cqrs.command.im.ImMessageAckCmd;
import com.co.kc.imchat.service.message.application.notification.task.ReceiptType;
import com.co.kc.imchat.service.message.model.enums.ImNotificationReceiptTypeEnum;
import com.co.kc.imchat.service.message.model.io.im.ImNotificationReceiptRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImMessageHttpIoTransformerTest {

    @Test
    void imMessageAckCmdMapsUserMessageAndPrivateSendReceiptType() {
        ImNotificationReceiptRequest request = receiptRequest(ImNotificationReceiptTypeEnum.PRIVATE_MESSAGE_SEND);

        ImMessageAckCmd command = ImMessageHttpIoTransformer.INSTANCE.imMessageAckCmdFrom(2L, request);

        assertThat(command.userId()).isEqualTo(2L);
        assertThat(command.chatId()).isEqualTo(102L);
        assertThat(command.messageId()).isEqualTo(900L);
        assertThat(command.receiptType()).isEqualTo(ReceiptType.PRIVATE_MESSAGE_SEND);
    }

    @Test
    void receiptTypeFromMapsAllNotificationReceiptTypes() {
        assertThat(ImMessageHttpIoTransformer.INSTANCE.receiptTypeFrom(ImNotificationReceiptTypeEnum.PRIVATE_MESSAGE_SEND))
                .isEqualTo(ReceiptType.PRIVATE_MESSAGE_SEND);
        assertThat(ImMessageHttpIoTransformer.INSTANCE.receiptTypeFrom(ImNotificationReceiptTypeEnum.PRIVATE_MESSAGE_REVOKE))
                .isEqualTo(ReceiptType.PRIVATE_MESSAGE_REVOKE);
        assertThat(ImMessageHttpIoTransformer.INSTANCE.receiptTypeFrom(ImNotificationReceiptTypeEnum.GROUP_MESSAGE_SEND))
                .isEqualTo(ReceiptType.GROUP_MESSAGE_SEND);
        assertThat(ImMessageHttpIoTransformer.INSTANCE.receiptTypeFrom(ImNotificationReceiptTypeEnum.GROUP_MESSAGE_REVOKE))
                .isEqualTo(ReceiptType.GROUP_MESSAGE_REVOKE);
    }

    private ImNotificationReceiptRequest receiptRequest(ImNotificationReceiptTypeEnum receiptType) {
        ImNotificationReceiptRequest request = new ImNotificationReceiptRequest();
        request.setChatId(102L);
        request.setMessageId(900L);
        request.setReceiptType(receiptType);
        return request;
    }
}
