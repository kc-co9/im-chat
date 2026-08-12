package com.co.kc.imchat.service.message.infrastructure.notification;

import com.co.kc.imchat.service.message.application.notification.model.ImGroupRevokedNotification;
import com.co.kc.imchat.service.message.application.notification.task.ReceiptType;
import org.springframework.stereotype.Component;

@Component
public class GroupRevokedNotifier extends AbstractBrokerImMessageNotifier<ImGroupRevokedNotification> {

    public GroupRevokedNotifier(BrokerMessageNotifier brokerMessageNotifier) {
        super(brokerMessageNotifier);
    }

    @Override
    protected String command() {
        return "message.group.revoked";
    }

    @Override
    protected Long receiverId(ImGroupRevokedNotification notification) {
        return notification.receiverId();
    }

    @Override
    public ReceiptType receiptType() {
        return ReceiptType.GROUP_MESSAGE_REVOKE;
    }

    @Override
    public String receiptId(ImGroupRevokedNotification notification) {
        return receiptType().receiptId(notification.receiverId(), notification.chatId(), notification.messageId());
    }
}
