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

    @Transactional(rollbackFor = Exception.class)
    public void sendMessage(GroupMessageSendCmd command) {
        ImMessageId messageId = new ImMessageId(snowflakeId.next());
        ImChatId senderChatId = new ImChatId(command.getChatId());
        UserId senderId = new UserId(command.getSenderId());
        ImMessageToken messageToken = new ImMessageToken(command.getMessageToken());
        ImMessageContent messageContent = new ImMessageContent(command.getMessageType(), command.getMessageContent());

        ImGroupChat senderChat = imGroupChatRepository.find(senderChatId);
        if (senderChat == null) {
            throw new NotFoundException("聊天不存在");
        }
        if (!senderChat.contain(senderId)) {
            throw new BusinessException("请使用本人群聊会话的 chatId 发送消息");
        }
        GroupMember senderMember = groupMemberRepository.find(senderChat.getGroupId(), senderId);
        if (senderMember == null) {
            throw new BusinessException("请使用本人群聊会话的 chatId 发送消息");
        }
        Group group = groupRepository.find(senderChat.getGroupId());
        if (group == null) {
            throw new NotFoundException("群组不存在");
        }
        if (group.isDismissed()) {
            throw new BusinessException("群聊已解散");
        }
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
            GroupSentNotifyCmd notifyCmd =
                    ImMessageAppTransformer.INSTANCE.groupSentNotifyCmdFrom(
                            memberChat.getUserId().getValue(), memberChat.getId().getValue(), event);
            imMessageNotifierInvoker.invoke(notifyCmd);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void revokeMessage(GroupMessageRevokeCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageId messageId = new ImMessageId(command.getMessageId());

        ImGroupChat senderChat = imGroupChatRepository.find(chatId);
        if (senderChat == null) {
            throw new NotFoundException("聊天不存在");
        }
        if (!senderChat.contain(userId)) {
            throw new BusinessException("请使用本人群聊会话的 chatId 撤回消息");
        }
        GroupMember senderMember = groupMemberRepository.find(senderChat.getGroupId(), userId);
        if (senderMember == null) {
            throw new BusinessException("请使用本人群聊会话的 chatId 撤回消息");
        }
        Group group = groupRepository.find(senderChat.getGroupId());
        if (group == null) {
            throw new NotFoundException("群组不存在");
        }
        if (group.isDismissed()) {
            throw new BusinessException("群聊已解散");
        }

        ImGroupInboxMessage senderInboxMessage = imGroupInboxMessageRepository.find(chatId, userId, messageId)
                .orElseThrow(() -> new NotFoundException("消息不存在"));
        senderInboxMessage.revoke(userId);

        List<ImGroupInboxMessage> inboxMessages =
                imGroupInboxMessageRepository.findByGroupIdAndMessageId(senderChat.getGroupId(), messageId);
        for (ImGroupInboxMessage inboxMessage : inboxMessages) {
            if (inboxMessage.getChatId().equals(senderInboxMessage.getChatId())
                    && inboxMessage.getUserId().equals(senderInboxMessage.getUserId())) {
                continue;
            }
            inboxMessage.revoke(userId);
        }
        imGroupInboxMessageRepository.saveAll(inboxMessages);

        ImGroupMessageRevokedEvent event = imMessageService.newImMessageRevokedEvent(senderChat.getGroupId(), senderInboxMessage);
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

    @Transactional(rollbackFor = Exception.class)
    public void readMessage(GroupMessageReadCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageId messageId = new ImMessageId(command.getMessageId());

        ImGroupChat groupChat = imGroupChatRepository.find(chatId);
        if (groupChat == null) {
            throw new NotFoundException("聊天不存在");
        }
        if (!groupChat.contain(userId)) {
            throw new BusinessException("请使用本人群聊会话的 chatId 读取消息");
        }
        GroupMember groupMember = groupMemberRepository.find(groupChat.getGroupId(), userId);
        if (groupMember == null) {
            throw new BusinessException("请使用本人群聊会话的 chatId 读取消息");
        }
        Group group = groupRepository.find(groupChat.getGroupId());
        if (group == null) {
            throw new NotFoundException("群组不存在");
        }
        if (group.isDismissed()) {
            throw new BusinessException("群聊已解散");
        }

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

        ImGroupChat groupChat = imGroupChatRepository.find(chatId);
        if (groupChat == null) {
            throw new NotFoundException("聊天不存在");
        }
        if (!groupChat.contain(userId)) {
            throw new BusinessException("无法查看别人的聊天记录");
        }
        GroupMember groupMember = groupMemberRepository.find(groupChat.getGroupId(), userId);
        if (groupMember == null) {
            throw new BusinessException("无法查看别人的聊天记录");
        }
        Group group = groupRepository.find(groupChat.getGroupId());
        if (group == null) {
            throw new NotFoundException("群组不存在");
        }
        if (group.isDismissed()) {
            throw new BusinessException("群聊已解散");
        }

        List<ImGroupInboxMessage> messageList =
                imGroupInboxMessageRepository.queryHistory(chatId, userId, lastMessageId, query.getCount());
        return ImMessageAppTransformer.INSTANCE.groupMessageDtoListFrom(messageList);
    }

    public GroupMessageDTO queryMessageDetail(GroupMessageDetailQuery query) {
        UserId userId = new UserId(query.getUserId());
        ImChatId chatId = new ImChatId(query.getChatId());
        ImMessageToken messageToken = new ImMessageToken(query.getMessageToken());

        ImGroupChat groupChat = imGroupChatRepository.find(chatId);
        if (groupChat == null) {
            throw new NotFoundException("聊天不存在");
        }
        if (!groupChat.contain(userId)) {
            throw new BusinessException("无法查看别人的聊天记录");
        }
        GroupMember groupMember = groupMemberRepository.find(groupChat.getGroupId(), userId);
        if (groupMember == null) {
            throw new BusinessException("无法查看别人的聊天记录");
        }
        Group group = groupRepository.find(groupChat.getGroupId());
        if (group == null) {
            throw new NotFoundException("群组不存在");
        }
        if (group.isDismissed()) {
            throw new BusinessException("群聊已解散");
        }

        ImGroupInboxMessage message = imGroupInboxMessageRepository.queryDetail(chatId, userId, messageToken);
        if (message == null) {
            throw new NotFoundException("消息不存在");
        }
        return ImMessageAppTransformer.INSTANCE.groupMessageDtoFrom(message);
    }

}
