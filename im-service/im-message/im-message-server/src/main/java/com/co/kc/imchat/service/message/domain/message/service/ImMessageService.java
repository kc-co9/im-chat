package com.co.kc.imchat.service.message.domain.message.service;

import com.co.kc.imchat.service.message.domain.chat.model.GroupChatMembership;
import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.service.message.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.common.domain.group.model.MemberDescriptor;
import com.co.kc.imchat.common.domain.group.model.MemberDisplayName;
import com.co.kc.imchat.service.message.domain.message.transformer.ImMessageDomainTransformer;
import com.co.kc.imchat.service.message.domain.message.event.ImGroupMessageRevokedEvent;
import com.co.kc.imchat.service.message.domain.message.event.ImGroupMessageSentEvent;
import com.co.kc.imchat.service.message.domain.message.event.ImPrivateMessageRevokedEvent;
import com.co.kc.imchat.service.message.domain.message.event.ImPrivateMessageSentEvent;
import com.co.kc.imchat.service.message.domain.message.factory.ImSystemMessageTokenFactory;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupInboxMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupMessageRevocation;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupMessageStatus;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupMessageTransmission;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageContent;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageRecipient;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageSender;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageToken;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageType;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageTypeEnum;
import com.co.kc.imchat.service.message.domain.message.model.ImOutboundMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateInboxMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateMessageRevocation;
import com.co.kc.imchat.service.message.domain.message.model.ImSystemMessageType;
import com.co.kc.imchat.service.message.domain.message.repository.ImGroupInboxMessageRepository;
import com.co.kc.imchat.service.message.domain.message.repository.ImPrivateInboxMessageRepository;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.exception.RepeatException;
import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
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
                ImSystemMessageTokenFactory.createSystemGroupCreated(chatMembership.groupId()),
                new ImMessageContent(ImMessageType.SYSTEM, ImSystemMessageType.GROUP_CREATED.content()));
        ImGroupChat ownerChat = chatMembership.findOwnerChat(ownerId)
                .orElseThrow(() -> new IllegalStateException("群主会话不存在"));
        ImMessageSender sender = new ImMessageSender(ownerChat, ownerId);
        List<ImMessageRecipient> recipients = chatMembership.chats().stream()
                .map(chat -> new ImMessageRecipient(chat, chat.belongsTo(ownerId)))
                .collect(Collectors.toList());
        return this.transmitGroupMessage(outboundMessage, sender, recipients);
    }

    public ImGroupMessageTransmission transmitGroupDismissed(UserId ownerId,
                                                             GroupChatMembership chatMembership) {
        ImOutboundMessage outboundMessage = new ImOutboundMessage(
                new ImMessageId(snowflakeId.next()),
                ImSystemMessageTokenFactory.createSystemGroupDismissed(chatMembership.groupId()),
                new ImMessageContent(ImMessageType.SYSTEM, ImSystemMessageType.GROUP_DISMISSED.content()));
        ImGroupChat ownerChat = chatMembership.findOwnerChat(ownerId)
                .orElseThrow(() -> new IllegalStateException("群主会话不存在"));
        ImMessageSender sender = new ImMessageSender(ownerChat, ownerId);
        List<ImMessageRecipient> recipients = chatMembership.chats().stream()
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
                ImSystemMessageTokenFactory.createSystemGroupMemberJoined(chatMembership.groupId(), messageId),
                new ImMessageContent(ImMessageType.SYSTEM, this.buildGroupMemberJoinedMessage(joinedMembers)));
        ImGroupChat inviterChat = chatMembership.findMemberChat(inviterId)
                .orElseThrow(() -> new IllegalStateException("邀请人群聊会话不存在"));
        ImMessageSender sender = new ImMessageSender(inviterChat, inviterId);
        List<ImMessageRecipient> recipients = chatMembership.chats().stream()
                .map(chat -> new ImMessageRecipient(chat, chat.belongsTo(inviterId)))
                .collect(Collectors.toList());
        return this.transmitGroupMessage(outboundMessage, sender, recipients);
    }

    private String buildGroupMemberJoinedMessage(List<MemberDescriptor> joinedMembers) {
        if (CollectionUtils.isEmpty(joinedMembers)) {
            return ImSystemMessageType.GROUP_MEMBER_JOINED.content();
        }
        String joinedNames = joinedMembers.stream()
                .map(MemberDescriptor::displayName)
                .filter(Objects::nonNull)
                .map(MemberDisplayName::value)
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
        ImGroupChat receiverChat = (ImGroupChat) recipient.chat();
        ImGroupInboxMessage imGroupInboxMessage = inboxMessages.stream()
                .filter(message -> message.getChatId().equals(receiverChat.getId()))
                .filter(message -> message.getUserId().equals(receiverChat.getUserId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("群聊消息不存在"));

        boolean readImmediately = receiverChat.belongsTo(sender.userId()) || recipient.chatting();
        receiverChat.receiveLatestMessage(imGroupInboxMessage, readImmediately);
        return receiverChat;
    }

    private ImGroupInboxMessage buildGroupInboxMessage(ImOutboundMessage outboundMessage,
                                                       ImMessageSender sender, ImMessageRecipient recipient) {
        ImGroupChat senderChat = (ImGroupChat) sender.chat();
        ImGroupChat receiverChat = (ImGroupChat) recipient.chat();
        boolean readImmediately = receiverChat.belongsTo(sender.userId()) || recipient.chatting();
        return ImGroupInboxMessage.builder()
                .id(outboundMessage.id())
                .token(outboundMessage.token())
                .content(outboundMessage.content())
                .groupId(senderChat.getGroupId())
                .chatId(receiverChat.getId())
                .userId(receiverChat.getUserId())
                .senderId(sender.userId())
                .status(readImmediately ? ImGroupMessageStatus.READ : ImGroupMessageStatus.SENT)
                .sendTime(outboundMessage.sendTime())
                .receivedTime(readImmediately ? outboundMessage.sendTime() : null)
                .readTime(readImmediately ? outboundMessage.sendTime() : null)
                .build();
    }

    public ImPrivateMessageSentEvent newImMessageSentEvent(ImPrivateInboxMessage imMessage) {
        ImMessageTypeEnum imMessageTypeEnum =
                ImMessageDomainTransformer.imMessageTypeEnumFrom(imMessage.getContent().type());
        ImPrivateMessageSentEvent imMessageSentEvent = new ImPrivateMessageSentEvent();
        imMessageSentEvent.setMessageId(imMessage.getId().value());
        imMessageSentEvent.setReceiverChatId(imMessage.getChatId().value());
        imMessageSentEvent.setSenderId(imMessage.getSenderId().value());
        imMessageSentEvent.setReceiverId(imMessage.getUserId().value());
        imMessageSentEvent.setMessageType(imMessageTypeEnum);
        imMessageSentEvent.setMessageContent(imMessage.getContent().value());
        imMessageSentEvent.setSendTime(imMessage.getSendTime());
        imMessageSentEvent.setCreateTime(LocalDateTime.now());
        return imMessageSentEvent;
    }

    public ImPrivateMessageRevokedEvent newImMessageRevokedEvent(ImPrivateInboxMessage imMessage, UserId receiverId) {
        ImPrivateMessageRevokedEvent imMessageRevokedEvent = new ImPrivateMessageRevokedEvent();
        imMessageRevokedEvent.setChatId(imMessage.getChatId().value());
        imMessageRevokedEvent.setReceiverId(receiverId.value());
        imMessageRevokedEvent.setMessageId(imMessage.getId().value());
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

    public ImGroupMessageSentEvent newImMessageSentEvent(GroupId groupId, ImGroupInboxMessage imMessage) {
        ImMessageTypeEnum imMessageTypeEnum =
                ImMessageDomainTransformer.imMessageTypeEnumFrom(imMessage.getContent().type());
        ImGroupMessageSentEvent event = new ImGroupMessageSentEvent();
        event.setMessageId(imMessage.getId().value());
        event.setGroupId(groupId.value());
        event.setSenderId(imMessage.getSenderId().value());
        event.setMessageType(imMessageTypeEnum);
        event.setMessageContent(imMessage.getContent().value());
        event.setSendTime(imMessage.getSendTime());
        event.setCreateTime(LocalDateTime.now());
        return event;
    }

    public ImGroupMessageRevokedEvent newImMessageRevokedEvent(GroupId groupId, ImGroupInboxMessage imMessage) {
        ImGroupMessageRevokedEvent event = new ImGroupMessageRevokedEvent();
        event.setGroupId(groupId.value());
        event.setMessageId(imMessage.getId().value());
        event.setSenderId(imMessage.getSenderId().value());
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
