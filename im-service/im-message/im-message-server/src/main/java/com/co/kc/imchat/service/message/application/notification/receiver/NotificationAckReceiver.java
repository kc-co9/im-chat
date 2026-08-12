package com.co.kc.imchat.service.message.application.notification.receiver;

import com.co.kc.imchat.service.message.model.cqrs.command.im.ImMessageAckCmd;
import com.co.kc.imchat.service.message.application.notification.task.ReceiptType;

public interface NotificationAckReceiver {

    ReceiptType receiptType();

    void receive(ImMessageAckCmd command);
}
