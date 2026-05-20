package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.domain.chat.GroupChatMembership;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.group.GroupId;
import com.co.kc.imchat.domain.group.MemberDescriptor;
import com.co.kc.imchat.domain.group.MemberDisplayName;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.model.enums.ImMessageTypeEnum;
import com.co.kc.imchat.support.exception.NotFoundException;
import com.co.kc.imchat.support.exception.RepeatException;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.transformer.application.ImMessageAppTransformer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * IM消息-领域服务
 */
@RequiredArgsConstructor
public class ImMessageService {

    private final ImGroupInboxMessageRepository imGroupInboxMessageRepository;
    private final ImPrivateInboxMessageRepository imPrivateInboxMessageRepository;
    private final SnowflakeId snowflakeId;

    public void ensureGroupMessageUnique(ImChatId chatId, UserId userId, ImMessageToken messageToken) {
        if (imGroupInboxMessageRepository.contain(chatId, userId, messageToken)) {
            throw new RepeatException("消息已存在");
        }
    }

    public void ensurePrivateMessageUnique(ImChatId chatId, ImMessageToken messageToken) {
        if (imPrivateInboxMessageRepository.contain(chatId, messageToken)) {
            throw new RepeatException("消息已存在");
        }
    }

    public ImGroupMessageTransmission transmitGroupCreated(UserId ownerId, GroupChatMembership chatMembership) {
        ImOutboundMessage outboundMessage = new ImOutboundMessage(
                new ImMessageId(snowflakeId.next()),
                ImSystemMessageTokenFactory.createSystemGroupCreated(chatMembership.getGroupId()),
                new ImMessageContent(ImMessageType.SYSTEM, ImSystemMessageType.GROUP_CREATED.content()));
        ImGroupChat ownerChat = chatMembership.findOwnerChat(ownerId)
                .orElseThrow(() -> new IllegalStateException("群主会话不存在"));
        ImMessageSender sender = new ImMessageSender(ownerChat, ownerId);
        List<ImMessageRecipient> recipients = chatMembership.getChats().stream()
                .map(chat -> new ImMessageRecipient(chat, chat.belongsTo(ownerId)))
                .collect(Collectors.toList());
        return this.transmitGroupMessage(outboundMessage, sender, recipients);
    }

    public ImGroupMessageTransmission transmitGroupDismissed(UserId ownerId,
                                                             GroupChatMembership chatMembership) {
        ImOutboundMessage outboundMessage = new ImOutboundMessage(
                new ImMessageId(snowflakeId.next()),
                ImSystemMessageTokenFactory.createSystemGroupDismissed(chatMembership.getGroupId()),
                new ImMessageContent(ImMessageType.SYSTEM, ImSystemMessageType.GROUP_DISMISSED.content()));
        ImGroupChat ownerChat = chatMembership.findOwnerChat(ownerId)
                .orElseThrow(() -> new IllegalStateException("群主会话不存在"));
        ImMessageSender sender = new ImMessageSender(ownerChat, ownerId);
        List<ImMessageRecipient> recipients = chatMembership.getChats().stream()
                .map(chat -> new ImMessageRecipient(chat, chat.belongsTo(ownerId)))
                .collect(Collectors.toList());
        return this.transmitGroupMessage(outboundMessage, sender, recipients);
    }

    public ImGroupMessageTransmission transmitGroupMemberJoined(UserId inviterId,
                                                                GroupChatMembership chatMembership,
                                                                List<MemberDescriptor> joinedMembers) {
        ImMessageId messageId = new ImMessageId(snowflakeId.next());
        ImOutboundMessage outboundMessage = new ImOutboundMessage(
                messageId,
                ImSystemMessageTokenFactory.createSystemGroupMemberJoined(chatMembership.getGroupId(), messageId),
                new ImMessageContent(ImMessageType.SYSTEM, this.buildGroupMemberJoinedMessage(joinedMembers)));
        ImGroupChat inviterChat = chatMembership.findMemberChat(inviterId)
                .orElseThrow(() -> new IllegalStateException("邀请人群聊会话不存在"));
        ImMessageSender sender = new ImMessageSender(inviterChat, inviterId);
        List<ImMessageRecipient> recipients = chatMembership.getChats().stream()
                .map(chat -> new ImMessageRecipient(chat, chat.belongsTo(inviterId)))
                .collect(Collectors.toList());
        return this.transmitGroupMessage(outboundMessage, sender, recipients);
    }

    private String buildGroupMemberJoinedMessage(List<MemberDescriptor> joinedMembers) {
        if (CollectionUtils.isEmpty(joinedMembers)) {
            return ImSystemMessageType.GROUP_MEMBER_JOINED.content();
        }
        String joinedNames = joinedMembers.stream()
                .map(MemberDescriptor::getDisplayName)
                .filter(Objects::nonNull)
                .map(MemberDisplayName::getValue)
                .collect(Collectors.joining("、"));
        return ImSystemMessageType.GROUP_MEMBER_JOINED.joinedContent(joinedNames);
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

    public ImPrivateMessageRevocation revokePrivateMessage(
            ImPrivateInboxMessage senderMessage, ImPrivateInboxMessage receiverMessage, UserId senderId) {
        if (!senderMessage.getSenderId().equals(senderId) || !receiverMessage.getSenderId().equals(senderId)) {
            throw new IllegalArgumentException("用户不能撤回别人的消息");
        }
        senderMessage.revoke(senderId);
        receiverMessage.revoke(senderId);
        return new ImPrivateMessageRevocation(senderMessage, receiverMessage);
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

    public ImGroupMessageRevocation revokeGroupMessage(
            List<ImGroupInboxMessage> messages, ImGroupInboxMessage senderMessage, UserId senderId) {
        if (CollectionUtils.isEmpty(messages)) {
            throw new NotFoundException("消息不存在");
        }
        ImGroupInboxMessage senderMessageCopy = messages.stream()
                .filter(message -> message.getChatId().equals(senderMessage.getChatId()))
                .filter(message -> message.getUserId().equals(senderMessage.getUserId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("消息不存在"));
        messages.forEach(message -> message.revoke(senderId));
        return new ImGroupMessageRevocation(messages, senderMessageCopy);
    }

    public List<ImGroupInboxMessage> readUnreadGroupMessages(ImGroupChat groupChat, UserId userId) {
        List<ImGroupInboxMessage> unreadMessages =
                imGroupInboxMessageRepository.findUnreadMessages(groupChat.getId(), userId);
        unreadMessages.forEach(message -> message.read(userId));
        return unreadMessages;
    }
}
