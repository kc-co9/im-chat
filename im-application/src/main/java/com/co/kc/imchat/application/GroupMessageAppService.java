package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.model.ImChatId;
import com.co.kc.imchat.domain.chat.service.ImChatService;
import com.co.kc.imchat.domain.group.model.Group;
import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.domain.chat.repository.ImGroupChatRepository;
import com.co.kc.imchat.domain.group.repository.GroupRepository;
import com.co.kc.imchat.domain.group.service.GroupService;
import com.co.kc.imchat.domain.group.model.GroupMember;
import com.co.kc.imchat.domain.group.repository.GroupMemberRepository;
import com.co.kc.imchat.domain.message.model.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.repository.ImGroupInboxMessageRepository;
import com.co.kc.imchat.domain.message.model.ImGroupMessageRevocation;
import com.co.kc.imchat.domain.message.event.ImGroupMessageRevokedEvent;
import com.co.kc.imchat.domain.message.event.ImGroupMessageSentEvent;
import com.co.kc.imchat.domain.message.model.ImGroupMessageTransmission;
import com.co.kc.imchat.domain.message.model.ImMessageContent;
import com.co.kc.imchat.domain.message.model.ImMessageId;
import com.co.kc.imchat.domain.message.model.ImMessageRecipient;
import com.co.kc.imchat.domain.message.service.ImMessageService;
import com.co.kc.imchat.domain.message.model.ImMessageSender;
import com.co.kc.imchat.domain.message.model.ImMessageToken;
import com.co.kc.imchat.domain.message.model.ImOutboundMessage;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.domain.user.service.UserService;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupMessageReceiveCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupMessageRevokeCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupMessageReadCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupMessageSendCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupRevokedNotifyCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupSentNotifyCmd;
import com.co.kc.imchat.application.model.cqrs.dto.group.GroupMessageDTO;
import com.co.kc.imchat.application.model.cqrs.query.group.GroupMessageDetailQuery;
import com.co.kc.imchat.application.model.cqrs.query.group.GroupMessageHistoryQuery;
import com.co.kc.imchat.application.support.event.DomainEventPublisher;
import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.application.support.lock.DistributeLockScene;
import com.co.kc.imchat.application.support.lock.annotation.DistributeLock;
import com.co.kc.imchat.application.support.notifier.ImMessageNotifierInvoker;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.application.transformer.ImMessageAppTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
public class GroupMessageAppService {
    private final ImGroupChatRepository imGroupChatRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ImGroupInboxMessageRepository imGroupInboxMessageRepository;

    private final UserService userService;
    private final GroupService groupService;
    private final ImMessageService imMessageService;
    private final ImChatService imChatService;

    private final SnowflakeId snowflakeId;
    private final ImMessageNotifierInvoker imMessageNotifierInvoker;
    private final DomainEventPublisher imMessageEventPublisher;

    @DistributeLock(scene = DistributeLockScene.GROUP_MESSAGE_SEND, key = "#command.chatId + ':' + #command.messageToken")
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void sendMessage(GroupMessageSendCmd command) {
        ImMessageId messageId = new ImMessageId(snowflakeId.next());
        ImChatId senderChatId = new ImChatId(command.getChatId());
        UserId senderId = new UserId(command.getSenderId());
        ImMessageToken messageToken = new ImMessageToken(command.getMessageToken());
        ImMessageContent messageContent = new ImMessageContent(command.getMessageType(), command.getMessageContent());

        ImGroupChat senderChat = imGroupChatRepository.find(senderChatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(senderChat, senderId);

        Group group = groupRepository.find(senderChat.getGroupId()).orElseThrow(() -> new NotFoundException("群组不存在"));
        groupService.ensureGroupMember(group, senderId);

        imMessageService.ensureGroupMessageUnique(senderChatId, senderId, messageToken);

        ImMessageSender imMessageSender = new ImMessageSender(senderChat, senderId);
        ImOutboundMessage outboundMessage = new ImOutboundMessage(messageId, messageToken, messageContent);
        List<ImMessageRecipient> recipients = groupService.findMessageRecipients(
                senderChat.getGroupId(), chat -> userService.isChatting(chat.getId(), chat.getUserId()));
        ImGroupMessageTransmission transmission =
                imMessageService.transmitGroupMessage(outboundMessage, imMessageSender, recipients);

        imGroupInboxMessageRepository.save(transmission.getInboxMessages());
        imGroupChatRepository.save(transmission.getGroupChats());

        ImGroupMessageSentEvent event =
                imMessageService.newImMessageSentEvent(senderChat.getGroupId(), transmission.getSenderMessage(senderId));
        imMessageEventPublisher.publish(event);
    }

    public void onMessageSent(ImGroupMessageSentEvent event) {
        GroupId groupId = new GroupId(event.getGroupId());
        UserId senderId = new UserId(event.getSenderId());
        List<GroupMember> memberList = groupMemberRepository.find(groupId);
        List<UserId> memberUserIds = FunctionUtils.mappingList(memberList, GroupMember::getUserId);
        List<ImGroupChat> memberChats = imGroupChatRepository.find(groupId, memberUserIds);
        for (ImGroupChat memberChat : memberChats) {
            if (memberChat.belongsTo(senderId)) {
                continue;
            }
            if (!userService.isOnline(memberChat.getUserId())) {
                continue;
            }
            GroupSentNotifyCmd notifyCmd =
                    ImMessageAppTransformer.INSTANCE.groupSentNotifyCmdFrom(
                            memberChat.getUserId().getValue(), memberChat.getId().getValue(), event);
            imMessageNotifierInvoker.invoke(notifyCmd);
        }
    }

    @DistributeLock(scene = DistributeLockScene.GROUP_MESSAGE_REVOKE, key = "#command.chatId + ':' + #command.messageId")
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void revokeMessage(GroupMessageRevokeCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageId messageId = new ImMessageId(command.getMessageId());

        ImGroupChat senderChat = imGroupChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(senderChat, userId);

        Group group = groupRepository.find(senderChat.getGroupId()).orElseThrow(() -> new NotFoundException("群组不存在"));
        groupService.ensureGroupMember(group, userId);

        ImGroupInboxMessage senderInboxMessage = imGroupInboxMessageRepository.find(chatId, userId, messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));

        List<ImGroupInboxMessage> inboxMessages =
                imGroupInboxMessageRepository.findByGroupIdAndMessageId(senderChat.getGroupId(), messageId);
        ImGroupMessageRevocation revocation =
                imMessageService.revokeGroupMessage(inboxMessages, senderInboxMessage, userId);
        imGroupInboxMessageRepository.save(revocation.getMessages());

        ImGroupMessageRevokedEvent event =
                imMessageService.newImMessageRevokedEvent(senderChat.getGroupId(), revocation.getSenderMessage());
        imMessageEventPublisher.publish(event);
    }

    public void onMessageRevoked(ImGroupMessageRevokedEvent event) {
        GroupId groupId = new GroupId(event.getGroupId());
        List<GroupMember> memberList = groupMemberRepository.find(groupId);
        List<UserId> memberUserIds = FunctionUtils.mappingList(memberList, GroupMember::getUserId);
        List<ImGroupChat> memberChats = imGroupChatRepository.find(groupId, memberUserIds);
        for (ImGroupChat memberChat : memberChats) {
            GroupRevokedNotifyCmd notifyCmd =
                    ImMessageAppTransformer.INSTANCE.groupRevokedNotifyCmdFrom(
                            memberChat.getUserId().getValue(), memberChat.getId().getValue(), event);
            imMessageNotifierInvoker.invoke(notifyCmd);
        }
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void receiveMessage(GroupMessageReceiveCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageId messageId = new ImMessageId(command.getMessageId());

        ImGroupChat groupChat = imGroupChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(groupChat, userId);

        Group group = groupRepository.find(groupChat.getGroupId()).orElseThrow(() -> new NotFoundException("群组不存在"));
        groupService.ensureGroupMember(group, userId);

        ImGroupInboxMessage message = imGroupInboxMessageRepository.find(chatId, userId, messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));
        message.receive(userId);
        imGroupInboxMessageRepository.save(message);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void readMessage(GroupMessageReadCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageId messageId = new ImMessageId(command.getMessageId());

        ImGroupChat groupChat = imGroupChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(groupChat, userId);

        Group group = groupRepository.find(groupChat.getGroupId()).orElseThrow(() -> new NotFoundException("群组不存在"));
        groupService.ensureGroupMember(group, userId);

        ImGroupInboxMessage message = imGroupInboxMessageRepository.find(chatId, userId, messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));
        groupChat.readMessage(message);

        imGroupInboxMessageRepository.save(message);
        imGroupChatRepository.save(groupChat);
    }

    public List<GroupMessageDTO> queryHistoryMessage(GroupMessageHistoryQuery query) {
        UserId userId = new UserId(query.getUserId());
        ImChatId chatId = new ImChatId(query.getChatId());
        ImMessageId lastMessageId = FunctionUtils.mappingOrNull(query.getLastMessageId(), ImMessageId::new);

        ImGroupChat groupChat = imGroupChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(groupChat, userId);

        Group group = groupRepository.find(groupChat.getGroupId()).orElseThrow(() -> new NotFoundException("群组不存在"));
        groupService.ensureGroupMember(group, userId);

        List<ImGroupInboxMessage> messageList =
                imGroupInboxMessageRepository.queryHistory(chatId, userId, lastMessageId, query.getCount());
        return ImMessageAppTransformer.INSTANCE.groupMessageDtoListFrom(messageList);
    }

    public GroupMessageDTO queryMessageDetail(GroupMessageDetailQuery query) {
        UserId userId = new UserId(query.getUserId());
        ImChatId chatId = new ImChatId(query.getChatId());
        ImMessageToken messageToken = new ImMessageToken(query.getMessageToken());

        ImGroupChat groupChat = imGroupChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(groupChat, userId);

        Group group = groupRepository.find(groupChat.getGroupId()).orElseThrow(() -> new NotFoundException("群组不存在"));
        groupService.ensureGroupMember(group, userId);

        ImGroupInboxMessage message = imGroupInboxMessageRepository.queryDetail(chatId, userId, messageToken);
        if (message == null) {
            throw new NotFoundException("消息不存在");
        }
        return ImMessageAppTransformer.INSTANCE.groupMessageDtoFrom(message);
    }

}
