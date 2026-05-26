package com.co.kc.imchat.application.support.notifier.receiver;

import com.co.kc.imchat.application.GroupMessageAppService;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupMessageReceiveCmd;
import com.co.kc.imchat.application.model.cqrs.command.im.ImMessageAckCmd;
import com.co.kc.imchat.application.support.notifier.task.ReceiptType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroupMessageSendAckReceiver implements NotificationAckReceiver {

    private final GroupMessageAppService groupMessageAppService;

    @Override
    public ReceiptType receiptType() {
        return ReceiptType.GROUP_MESSAGE_SEND;
    }

    @Override
    public void receive(ImMessageAckCmd command) {
        groupMessageAppService.receiveMessage(new GroupMessageReceiveCmd(command.userId(), command.chatId(), command.messageId()));
    }
}
