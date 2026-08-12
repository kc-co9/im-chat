package com.co.kc.imchat.service.message.interfaces.rpc;

import com.co.kc.imchat.service.message.application.GroupMessageAppService;
import com.co.kc.imchat.service.message.application.NotificationAckAppService;
import com.co.kc.imchat.service.message.application.PrivateMessageAppService;
import com.co.kc.imchat.service.message.application.notification.task.ReceiptType;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageType;
import com.co.kc.imchat.service.message.facade.MessageService;
import com.co.kc.imchat.service.message.facade.params.GroupMessageReadParams;
import com.co.kc.imchat.service.message.facade.params.GroupMessageRevokeParams;
import com.co.kc.imchat.service.message.facade.params.GroupMessageSendParams;
import com.co.kc.imchat.service.message.facade.params.NotificationAckParams;
import com.co.kc.imchat.service.message.facade.params.PrivateMessageReadParams;
import com.co.kc.imchat.service.message.facade.params.PrivateMessageRevokeParams;
import com.co.kc.imchat.service.message.facade.params.PrivateMessageSendParams;
import com.co.kc.imchat.service.message.model.cqrs.command.group.GroupMessageReadCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.group.GroupMessageRevokeCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.group.GroupMessageSendCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.im.ImMessageAckCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.im.ImPrivateMessageReadCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.im.ImPrivateMessageRevokeCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.im.ImPrivateMessageSendCmd;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

@RequiredArgsConstructor
@DubboService(interfaceClass = MessageService.class, version = "1.0.0")
public class MessageRpcService implements MessageService {
    private final PrivateMessageAppService privateMessageAppService;
    private final GroupMessageAppService groupMessageAppService;
    private final NotificationAckAppService notificationAckAppService;

    @Override
    public void sendPrivateMessage(PrivateMessageSendParams params) {
        privateMessageAppService.sendMessage(new ImPrivateMessageSendCmd(
                params.userId(),
                params.chatId(),
                params.messageToken(),
                ImMessageType.valueOf(params.messageType()),
                params.messageContent()
        ));
    }

    @Override
    public void readPrivateMessage(PrivateMessageReadParams params) {
        privateMessageAppService.readMessage(new ImPrivateMessageReadCmd(
                params.chatId(),
                params.userId(),
                params.messageId()
        ));
    }

    @Override
    public void revokePrivateMessage(PrivateMessageRevokeParams params) {
        privateMessageAppService.revokeMessage(new ImPrivateMessageRevokeCmd(
                params.userId(),
                params.chatId(),
                params.messageId()
        ));
    }

    @Override
    public void sendGroupMessage(GroupMessageSendParams params) {
        groupMessageAppService.sendMessage(new GroupMessageSendCmd(
                params.chatId(),
                params.userId(),
                params.messageToken(),
                ImMessageType.valueOf(params.messageType()),
                params.messageContent()
        ));
    }

    @Override
    public void readGroupMessage(GroupMessageReadParams params) {
        groupMessageAppService.readMessage(new GroupMessageReadCmd(
                params.userId(),
                params.chatId(),
                params.messageId()
        ));
    }

    @Override
    public void revokeGroupMessage(GroupMessageRevokeParams params) {
        groupMessageAppService.revokeMessage(new GroupMessageRevokeCmd(
                params.userId(),
                params.chatId(),
                params.messageId()
        ));
    }

    @Override
    public void ackNotification(NotificationAckParams params) {
        notificationAckAppService.confirmMessage(new ImMessageAckCmd(
                params.userId(),
                params.chatId(),
                params.messageId(),
                ReceiptType.valueOf(params.receiptType())
        ));
    }
}
