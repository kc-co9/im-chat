package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.group.GroupId;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.model.enums.ImMessageTypeEnum;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.transformer.application.ImMessageAppTransformer;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * IM消息-领域服务
 */
@RequiredArgsConstructor
public class ImMessageService {

    private final SnowflakeId snowflakeId;

    public ImGroupMessageTransmission transmitGroupCreated(GroupId groupId,
                                                           UserId ownerId,
                                                           ImGroupChat ownerChat,
                                                           List<ImGroupChat> groupChats) {
        ImOutboundMessage outboundMessage = new ImOutboundMessage(
                new ImMessageId(snowflakeId.next()),
                ImSystemMessageTokenFactory.createSystemGroupCreated(groupId),
                new ImMessageContent(ImMessageType.SYSTEM, "群聊已创建"));
        ImMessageSender sender = new ImMessageSender(ownerChat, ownerId);
        List<ImMessageRecipient> recipients = groupChats.stream()
                .map(chat -> new ImMessageRecipient(chat, chat.belongsTo(ownerId)))
                .collect(Collectors.toList());
        return this.transmitGroupMessage(outboundMessage, sender, recipients);
    }

    public ImGroupMessageTransmission transmitGroupDismissed(GroupId groupId,
                                                             UserId ownerId,
                                                             ImGroupChat ownerChat,
                                                             List<ImGroupChat> groupChats) {
        ImOutboundMessage outboundMessage = new ImOutboundMessage(
                new ImMessageId(snowflakeId.next()),
                ImSystemMessageTokenFactory.createSystemGroupDismissed(groupId),
                new ImMessageContent(ImMessageType.SYSTEM, "群聊已解散"));
        ImMessageSender sender = new ImMessageSender(ownerChat, ownerId);
        List<ImMessageRecipient> recipients = groupChats.stream()
                .map(chat -> new ImMessageRecipient(chat, chat.belongsTo(ownerId)))
                .collect(Collectors.toList());
        return this.transmitGroupMessage(outboundMessage, sender, recipients);
    }

    public ImGroupMessageTransmission transmitGroupMessage(ImOutboundMessage outboundMessage,
                                                           ImMessageSender sender, List<ImMessageRecipient> recipients) {
        List<ImGroupInboxMessage> inboxMessages = recipients.stream()
                .map(recipient -> this.buildGroupInboxMessage(outboundMessage, sender, recipient))
                .collect(Collectors.toList());
        List<ImGroupChat> groupChats = recipients.stream()
                .map(recipient -> this.receiveGroupMessage(recipient, inboxMessages, sender))
                .collect(Collectors.toList());
        return new ImGroupMessageTransmission(inboxMessages, groupChats);
    }

    private ImGroupChat receiveGroupMessage(ImMessageRecipient recipient,
                                            List<ImGroupInboxMessage> inboxMessages,
                                            ImMessageSender sender) {
        ImGroupChat receiverChat = (ImGroupChat) recipient.getChat();
        ImGroupInboxMessage imGroupInboxMessage = inboxMessages.stream()
                .filter(message -> message.getChatId().equals(receiverChat.getId()))
                .filter(message -> message.getUserId().equals(receiverChat.getUserId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("群聊消息不存在"));

        boolean isSender = receiverChat.belongsTo(sender.getUserId());
        receiverChat.receiveLatestMessage(imGroupInboxMessage, isSender);
        return receiverChat;
    }

    private ImGroupInboxMessage buildGroupInboxMessage(ImOutboundMessage outboundMessage,
                                                       ImMessageSender sender, ImMessageRecipient recipient) {
        ImGroupChat senderChat = (ImGroupChat) sender.getChat();
        ImGroupChat receiverChat = (ImGroupChat) recipient.getChat();
        boolean isSender = receiverChat.belongsTo(sender.getUserId());
        return ImGroupInboxMessage.builder()
                .id(outboundMessage.getId())
                .token(outboundMessage.getToken())
                .content(outboundMessage.getContent())
                .groupId(senderChat.getGroupId())
                .chatId(receiverChat.getId())
                .userId(receiverChat.getUserId())
                .senderId(sender.getUserId())
                .status(isSender ? ImGroupMessageStatus.READ : ImGroupMessageStatus.SENT)
                .sendTime(outboundMessage.getSendTime())
                .receivedTime(isSender ? outboundMessage.getSendTime() : null)
                .readTime(isSender ? outboundMessage.getSendTime() : null)
                .build();
    }

    public ImPrivateMessageSentEvent newImMessageSentEvent(ImPrivateInboxMessage imMessage) {
        ImMessageTypeEnum imMessageTypeEnum =
                ImMessageAppTransformer.INSTANCE.imMessageTypeEnumFrom(imMessage.getContent().getType());
        ImPrivateMessageSentEvent imMessageSentEvent = new ImPrivateMessageSentEvent();
        imMessageSentEvent.setMessageId(imMessage.getId().getValue());
        imMessageSentEvent.setReceiverChatId(imMessage.getChatId().getValue());
        imMessageSentEvent.setSenderId(imMessage.getSenderId().getValue());
        imMessageSentEvent.setReceiverId(imMessage.getUserId().getValue());
        imMessageSentEvent.setMessageType(imMessageTypeEnum);
        imMessageSentEvent.setMessageContent(imMessage.getContent().getValue());
        imMessageSentEvent.setSendTime(imMessage.getSendTime());
        imMessageSentEvent.setCreateTime(LocalDateTime.now());
        return imMessageSentEvent;
    }

    public ImPrivateMessageRevokedEvent newImMessageRevokedEvent(ImPrivateInboxMessage imMessage, UserId receiverId) {
        ImPrivateMessageRevokedEvent imMessageRevokedEvent = new ImPrivateMessageRevokedEvent();
        imMessageRevokedEvent.setChatId(imMessage.getChatId().getValue());
        imMessageRevokedEvent.setReceiverId(receiverId.getValue());
        imMessageRevokedEvent.setMessageId(imMessage.getId().getValue());
        imMessageRevokedEvent.setCreateTime(LocalDateTime.now());
        return imMessageRevokedEvent;
    }

    public ImPrivateMessageReceivedEvent newImMessageReceivedEvent(ImPrivateInboxMessage imMessage) {
        ImPrivateMessageReceivedEvent imMessageReceivedEvent = new ImPrivateMessageReceivedEvent();
        imMessageReceivedEvent.setReceiverChatId(imMessage.getChatId().getValue());
        imMessageReceivedEvent.setReceiverId(imMessage.getUserId().getValue());
        imMessageReceivedEvent.setMessageId(imMessage.getId().getValue());
        imMessageReceivedEvent.setCreateTime(LocalDateTime.now());
        return imMessageReceivedEvent;
    }

    public ImGroupMessageSentEvent newImMessageSentEvent(GroupId groupId, ImGroupInboxMessage imMessage) {
        ImMessageTypeEnum imMessageTypeEnum =
                ImMessageAppTransformer.INSTANCE.imMessageTypeEnumFrom(imMessage.getContent().getType());
        ImGroupMessageSentEvent event = new ImGroupMessageSentEvent();
        event.setMessageId(imMessage.getId().getValue());
        event.setGroupId(groupId.getValue());
        event.setSenderId(imMessage.getSenderId().getValue());
        event.setMessageType(imMessageTypeEnum);
        event.setMessageContent(imMessage.getContent().getValue());
        event.setSendTime(imMessage.getSendTime());
        event.setCreateTime(LocalDateTime.now());
        return event;
    }

    public ImGroupMessageRevokedEvent newImMessageRevokedEvent(GroupId groupId, ImGroupInboxMessage imMessage) {
        ImGroupMessageRevokedEvent event = new ImGroupMessageRevokedEvent();
        event.setGroupId(groupId.getValue());
        event.setMessageId(imMessage.getId().getValue());
        event.setSenderId(imMessage.getSenderId().getValue());
        event.setRevokeTime(imMessage.getRevokeTime());
        event.setCreateTime(LocalDateTime.now());
        return event;
    }
}
