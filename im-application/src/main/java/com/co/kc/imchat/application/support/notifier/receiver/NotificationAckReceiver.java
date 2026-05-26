package com.co.kc.imchat.application.support.notifier.receiver;

import com.co.kc.imchat.application.model.cqrs.command.im.ImMessageAckCmd;
import com.co.kc.imchat.application.support.notifier.task.ReceiptType;

public interface NotificationAckReceiver {

    ReceiptType receiptType();

    void receive(ImMessageAckCmd command);
}
