package com.co.kc.imchat.service.message.infrastructure.notification;

import com.co.kc.imchat.service.message.application.notification.model.ImGroupSentNotification;
import com.co.kc.imchat.service.message.application.notification.task.ReceiptType;
import org.springframework.stereotype.Component;

@Component
public class GroupSentNotifier extends AbstractBrokerImMessageNotifier<ImGroupSentNotification> {

    public GroupSentNotifier(BrokerMessageNotifier brokerMessageNotifier) {
        super(brokerMessageNotifier);
    }

    @Override
    protected String command() {
        return "message.group.sent";
    }

    @Override
    protected Long receiverId(ImGroupSentNotification notification) {
        return notification.receiverId();
    }

    @Override
    public ReceiptType receiptType() {
        return ReceiptType.GROUP_MESSAGE_SEND;
    }

    @Override
    public String receiptId(ImGroupSentNotification notification) {
        return receiptType().receiptId(notification.receiverId(), notification.chatId(), notification.messageId());
    }
}
