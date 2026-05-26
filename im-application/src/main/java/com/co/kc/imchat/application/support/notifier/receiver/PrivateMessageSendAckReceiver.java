package com.co.kc.imchat.application.support.notifier.receiver;

import com.co.kc.imchat.application.PrivateMessageAppService;
import com.co.kc.imchat.application.model.cqrs.command.im.ImPrivateMessageReceiveCmd;
import com.co.kc.imchat.application.model.cqrs.command.im.ImMessageAckCmd;
import com.co.kc.imchat.application.support.notifier.task.ReceiptType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PrivateMessageSendAckReceiver implements NotificationAckReceiver {

    private final PrivateMessageAppService privateMessageAppService;

    @Override
    public ReceiptType receiptType() {
        return ReceiptType.PRIVATE_MESSAGE_SEND;
    }

    @Override
    public void receive(ImMessageAckCmd command) {
        privateMessageAppService.receiveMessage(new ImPrivateMessageReceiveCmd(command.userId(), command.chatId(), command.messageId()));
    }
}
