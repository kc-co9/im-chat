package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.group.Group;
import com.co.kc.imchat.domain.group.GroupId;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupChatRepository;
import com.co.kc.imchat.domain.group.GroupRepository;
import com.co.kc.imchat.domain.group.GroupService;
import com.co.kc.imchat.domain.group.GroupMember;
import com.co.kc.imchat.domain.group.GroupMemberRepository;
import com.co.kc.imchat.domain.message.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.ImGroupInboxMessageRepository;
import com.co.kc.imchat.domain.message.ImGroupMessageRevokedEvent;
import com.co.kc.imchat.domain.message.ImGroupMessageSentEvent;
import com.co.kc.imchat.domain.message.ImGroupMessageTransmission;
import com.co.kc.imchat.domain.message.ImMessageContent;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.message.ImMessageRecipient;
import com.co.kc.imchat.domain.message.ImMessageService;
import com.co.kc.imchat.domain.message.ImMessageSender;
import com.co.kc.imchat.domain.message.ImMessageToken;
import com.co.kc.imchat.domain.message.ImOutboundMessage;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.domain.user.UserService;
import com.co.kc.imchat.model.cqrs.command.group.GroupMessageReceiveCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupMessageRevokeCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupMessageReadCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupMessageSendCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupRevokedNotifyCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupSentNotifyCmd;
import com.co.kc.imchat.model.cqrs.dto.group.GroupMessageDTO;
import com.co.kc.imchat.model.cqrs.query.group.GroupMessageDetailQuery;
import com.co.kc.imchat.model.cqrs.query.group.GroupMessageHistoryQuery;
import com.co.kc.imchat.support.event.DomainEventPublisher;
import com.co.kc.imchat.support.exception.BusinessException;
import com.co.kc.imchat.support.exception.NotFoundException;
import com.co.kc.imchat.support.exception.RepeatException;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.support.notifier.ImMessageNotifierInvoker;
import com.co.kc.imchat.support.utils.FunctionUtils;
import com.co.kc.imchat.transformer.application.ImMessageAppTransformer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
public class GroupMessageAppService {
    private final SnowflakeId snowflakeId;
    private final ImGroupChatRepository imGroupChatRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ImGroupInboxMessageRepository imGroupInboxMessageRepository;
    private final GroupRepository groupRepository;

    private final UserService userService;
    private final GroupService groupService;
    private final ImMessageService imMessageService;

    private final ImMessageNotifierInvoker imMessageNotifierInvoker;
    private final DomainEventPublisher imMessageEventPublisher;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void sendMessage(GroupMessageSendCmd command) {
        ImMessageId messageId = new ImMessageId(snowflakeId.next());
        ImChatId senderChatId = new ImChatId(command.getChatId());
        UserId senderId = new UserId(command.getSenderId());
        ImMessageToken messageToken = new ImMessageToken(command.getMessageToken());
        ImMessageContent messageContent = new ImMessageContent(command.getMessageType(), command.getMessageContent());

        ImGroupChat senderChat = requireUsableGroupChat(senderChatId, senderId, "请使用本人群聊会话的 chatId 发送消息");
        if (imGroupInboxMessageRepository.contain(senderChatId, senderId, messageToken)) {
            throw new RepeatException("消息已存在");
        }

        ImMessageSender imMessageSender = new ImMessageSender(senderChat, senderId);
        ImOutboundMessage outboundMessage = new ImOutboundMessage(messageId, messageToken, messageContent);
        List<ImMessageRecipient> recipients = groupService.findMessageRecipients(
                senderChat.getGroupId(), chat -> userService.isChatting(chat.getId(), chat.getUserId()));
        ImGroupMessageTransmission transmission =
                imMessageService.transmitGroupMessage(outboundMessage, imMessageSender, recipients);

        imGroupInboxMessageRepository.saveAll(transmission.getInboxMessages());
        imGroupChatRepository.saveAll(transmission.getGroupChats());

        ImGroupMessageSentEvent event =
                imMessageService.newImMessageSentEvent(senderChat.getGroupId(), transmission.getSenderMessage(senderId));
        imMessageEventPublisher.publish(event);
    }

    public void onMessageSent(ImGroupMessageSentEvent event) {
        GroupId groupId = new GroupId(event.getGroupId());
        List<GroupMember> memberList = groupMemberRepository.find(groupId);
        List<UserId> memberUserIds = FunctionUtils.mappingList(memberList, GroupMember::getUserId);
        List<ImGroupChat> memberChats = imGroupChatRepository.findByUserIdsAndGroupId(groupId, memberUserIds);
        for (ImGroupChat memberChat : memberChats) {
            if (memberChat.getUserId().getValue().equals(event.getSenderId())) {
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

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void revokeMessage(GroupMessageRevokeCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageId messageId = new ImMessageId(command.getMessageId());

        ImGroupChat senderChat = requireUsableGroupChat(chatId, userId, "请使用本人群聊会话的 chatId 撤回消息");

        ImGroupInboxMessage senderInboxMessage = imGroupInboxMessageRepository.find(chatId, userId, messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));

        List<ImGroupInboxMessage> inboxMessages =
                imGroupInboxMessageRepository.findByGroupIdAndMessageId(senderChat.getGroupId(), messageId);
        if (CollectionUtils.isEmpty(inboxMessages)) {
            throw new NotFoundException("消息不存在");
        }
        for (ImGroupInboxMessage inboxMessage : inboxMessages) {
            inboxMessage.revoke(userId);
        }
        imGroupInboxMessageRepository.saveAll(inboxMessages);

        ImGroupInboxMessage revokedSenderInboxMessage = findRevokedSenderInboxMessage(inboxMessages, senderInboxMessage);
        ImGroupMessageRevokedEvent event = imMessageService.newImMessageRevokedEvent(senderChat.getGroupId(), revokedSenderInboxMessage);
        imMessageEventPublisher.publish(event);
    }

    public void onMessageRevoked(ImGroupMessageRevokedEvent event) {
        GroupId groupId = new GroupId(event.getGroupId());
        List<GroupMember> memberList = groupMemberRepository.find(groupId);
        List<UserId> memberUserIds = FunctionUtils.mappingList(memberList, GroupMember::getUserId);
        List<ImGroupChat> memberChats = imGroupChatRepository.findByUserIdsAndGroupId(groupId, memberUserIds);
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

        requireUsableGroupChat(chatId, userId, "请使用本人群聊会话的 chatId 接收消息");

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

        ImGroupChat groupChat = requireUsableGroupChat(chatId, userId, "请使用本人群聊会话的 chatId 读取消息");

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

        requireUsableGroupChat(chatId, userId, "无法查看别人的聊天记录");

        List<ImGroupInboxMessage> messageList =
                imGroupInboxMessageRepository.queryHistory(chatId, userId, lastMessageId, query.getCount());
        return ImMessageAppTransformer.INSTANCE.groupMessageDtoListFrom(messageList);
    }

    public GroupMessageDTO queryMessageDetail(GroupMessageDetailQuery query) {
        UserId userId = new UserId(query.getUserId());
        ImChatId chatId = new ImChatId(query.getChatId());
        ImMessageToken messageToken = new ImMessageToken(query.getMessageToken());

        requireUsableGroupChat(chatId, userId, "无法查看别人的聊天记录");

        ImGroupInboxMessage message = imGroupInboxMessageRepository.queryDetail(chatId, userId, messageToken);
        if (message == null) {
            throw new NotFoundException("消息不存在");
        }
        return ImMessageAppTransformer.INSTANCE.groupMessageDtoFrom(message);
    }

    private ImGroupInboxMessage findRevokedSenderInboxMessage(
            List<ImGroupInboxMessage> inboxMessages, ImGroupInboxMessage senderInboxMessage) {
        return inboxMessages.stream()
                .filter(inboxMessage -> inboxMessage.getChatId().equals(senderInboxMessage.getChatId()))
                .filter(inboxMessage -> inboxMessage.getUserId().equals(senderInboxMessage.getUserId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("消息不存在"));
    }

    private ImGroupChat requireUsableGroupChat(ImChatId chatId, UserId userId, String permissionMessage) {
        ImGroupChat groupChat = imGroupChatRepository.find(chatId)
                .orElseThrow(() -> new NotFoundException("聊天不存在"));
        if (!groupChat.belongsTo(userId)) {
            throw new BusinessException(permissionMessage);
        }
        if (!groupMemberRepository.contain(groupChat.getGroupId(), userId)) {
            throw new BusinessException(permissionMessage);
        }
        Group group = groupRepository.find(groupChat.getGroupId())
                .orElseThrow(() -> new NotFoundException("群组不存在"));
        group.ensureActive();
        return groupChat;
    }
}
