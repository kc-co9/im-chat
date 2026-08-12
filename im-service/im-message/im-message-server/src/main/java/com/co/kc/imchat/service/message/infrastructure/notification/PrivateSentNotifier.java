package com.co.kc.imchat.service.message.infrastructure.notification;

import com.co.kc.imchat.service.message.application.notification.model.ImPrivateSentNotification;
import com.co.kc.imchat.service.message.application.notification.task.ReceiptType;
import org.springframework.stereotype.Component;

@Component
public class PrivateSentNotifier extends AbstractBrokerImMessageNotifier<ImPrivateSentNotification> {

    public PrivateSentNotifier(BrokerMessageNotifier brokerMessageNotifier) {
        super(brokerMessageNotifier);
    }

    @Override
    protected String command() {
        return "message.private.sent";
    }

    @Override
    protected Long receiverId(ImPrivateSentNotification notification) {
        return notification.receiverId();
    }

    @Override
    public ReceiptType receiptType() {
        return ReceiptType.PRIVATE_MESSAGE_SEND;
    }

    @Override
    public String receiptId(ImPrivateSentNotification notification) {
        return receiptType().receiptId(notification.receiverId(), notification.chatId(), notification.messageId());
    }
}
