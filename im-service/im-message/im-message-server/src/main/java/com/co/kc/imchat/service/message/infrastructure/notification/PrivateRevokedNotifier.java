package com.co.kc.imchat.service.message.infrastructure.notification;

import com.co.kc.imchat.service.message.application.notification.model.ImPrivateRevokedNotification;
import com.co.kc.imchat.service.message.application.notification.task.ReceiptType;
import org.springframework.stereotype.Component;

@Component
public class PrivateRevokedNotifier extends AbstractBrokerImMessageNotifier<ImPrivateRevokedNotification> {

    public PrivateRevokedNotifier(BrokerMessageNotifier brokerMessageNotifier) {
        super(brokerMessageNotifier);
    }

    @Override
    protected String command() {
        return "message.private.revoked";
    }

    @Override
    protected Long receiverId(ImPrivateRevokedNotification notification) {
        return notification.receiverId();
    }

    @Override
    public ReceiptType receiptType() {
        return ReceiptType.PRIVATE_MESSAGE_REVOKE;
    }

    @Override
    public String receiptId(ImPrivateRevokedNotification notification) {
        return receiptType().receiptId(notification.receiverId(), notification.receiverChatId(), notification.messageId());
    }
}
