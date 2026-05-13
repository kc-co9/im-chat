package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImGroupId;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupChatRepository;
import com.co.kc.imchat.domain.chat.ImGroupService;
import com.co.kc.imchat.domain.chat.ImGroupMember;
import com.co.kc.imchat.domain.chat.ImGroupMemberRepository;
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
import com.co.kc.imchat.model.cqrs.command.im.ImGroupMessageRevokeCmd;
import com.co.kc.imchat.model.cqrs.command.im.ImGroupMessageSendCmd;
import com.co.kc.imchat.model.cqrs.command.notify.ImGroupRevokedNotifyCmd;
import com.co.kc.imchat.model.cqrs.command.notify.ImGroupSentNotifyCmd;
import com.co.kc.imchat.model.cqrs.dto.im.ImGroupMessageDTO;
import com.co.kc.imchat.model.cqrs.query.ImGroupMessageDetailQuery;
import com.co.kc.imchat.model.cqrs.query.ImGroupMessageHistoryQuery;
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
public class ImGroupAppService {
    private final SnowflakeId snowflakeId;
    private final ImGroupChatRepository imGroupChatRepository;
    private final ImGroupMemberRepository imGroupMemberRepository;
    private final ImGroupInboxMessageRepository imGroupInboxMessageRepository;

    private final UserService userService;
    private final ImGroupService imGroupService;
    private final ImMessageService imMessageService;

    private final ImMessageNotifierInvoker imMessageNotifierInvoker;
    private final DomainEventPublisher imMessageEventPublisher;

    @Transactional(rollbackFor = Exception.class)
    public void sendMessage(ImGroupMessageSendCmd command) {
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
        ImGroupMember senderMember = imGroupMemberRepository.find(senderChat.getGroupId(), senderId);
        if (senderMember == null) {
            throw new BusinessException("请使用本人群聊会话的 chatId 发送消息");
        }
        if (imGroupInboxMessageRepository.contain(senderChatId, senderId, messageToken)) {
            throw new RepeatException("消息已存在");
        }

        ImMessageSender imMessageSender = new ImMessageSender(senderChat, senderId);
        ImOutboundMessage outboundMessage = new ImOutboundMessage(messageId, messageToken, messageContent);
        List<ImMessageRecipient> recipients = imGroupService.findMessageRecipients(
                senderChat.getGroupId(), chat -> userService.isChatting(chat.getId(), chat.getUserId()));
        ImGroupMessageTransmission transmission = imMessageService.transmitGroupMessage(outboundMessage, imMessageSender, recipients);

        imGroupInboxMessageRepository.saveAll(transmission.getInboxMessages());
        imGroupChatRepository.saveAll(transmission.getGroupChats());

        ImGroupMessageSentEvent event =
                imMessageService.newImMessageSentEvent(senderChat.getGroupId(), transmission.getSenderMessage(senderId));
        imMessageEventPublisher.publish(event);
    }

    public void onMessageSent(ImGroupMessageSentEvent event) {
        ImGroupId groupId = new ImGroupId(event.getGroupId());
        List<ImGroupMember> memberList = imGroupMemberRepository.find(groupId);
        List<UserId> memberUserIds = FunctionUtils.mappingList(memberList, ImGroupMember::getUserId);
        List<ImGroupChat> memberChats = imGroupChatRepository.findByUserIdsAndGroupId(groupId, memberUserIds);
        for (ImGroupChat memberChat : memberChats) {
            ImGroupSentNotifyCmd notifyCmd =
                    ImMessageAppTransformer.INSTANCE.imGroupSentNotifyCmdFrom(
                            memberChat.getUserId().getValue(), memberChat.getId().getValue(), event);
            imMessageNotifierInvoker.invoke(notifyCmd);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void revokeMessage(ImGroupMessageRevokeCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        ImMessageId messageId = new ImMessageId(command.getMessageId());

        ImGroupChat senderChat = imGroupChatRepository.find(chatId);
        if (senderChat == null) {
            throw new NotFoundException("聊天不存在");
        }
        ImGroupMember senderMember = imGroupMemberRepository.find(senderChat.getGroupId(), userId);
        if (senderMember == null) {
            throw new BusinessException("请使用本人群聊会话的 chatId 撤回消息");
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
        ImGroupId groupId = new ImGroupId(event.getGroupId());
        List<ImGroupMember> memberList = imGroupMemberRepository.find(groupId);
        List<UserId> memberUserIds = FunctionUtils.mappingList(memberList, ImGroupMember::getUserId);
        List<ImGroupChat> memberChats = imGroupChatRepository.findByUserIdsAndGroupId(groupId, memberUserIds);
        for (ImGroupChat memberChat : memberChats) {
            ImGroupRevokedNotifyCmd notifyCmd =
                    ImMessageAppTransformer.INSTANCE.imGroupRevokedNotifyCmdFrom(
                            memberChat.getUserId().getValue(), memberChat.getId().getValue(), event);
            imMessageNotifierInvoker.invoke(notifyCmd);
        }
    }

    public List<ImGroupMessageDTO> queryHistoryMessage(ImGroupMessageHistoryQuery query) {
        UserId userId = new UserId(query.getUserId());
        ImChatId chatId = new ImChatId(query.getChatId());
        ImMessageId lastMessageId = FunctionUtils.mappingOrNull(query.getLastMessageId(), ImMessageId::new);

        ImGroupChat groupChat = imGroupChatRepository.find(chatId);
        if (groupChat == null) {
            throw new NotFoundException("聊天不存在");
        }
        ImGroupMember groupMember = imGroupMemberRepository.find(groupChat.getGroupId(), userId);
        if (groupMember == null) {
            throw new BusinessException("无法查看别人的聊天记录");
        }

        List<ImGroupInboxMessage> messageList =
                imGroupInboxMessageRepository.queryHistory(chatId, userId, lastMessageId, query.getCount());
        return ImMessageAppTransformer.INSTANCE.imGroupMessageDtoListFrom(messageList);
    }

    public ImGroupMessageDTO queryMessageDetail(ImGroupMessageDetailQuery query) {
        UserId userId = new UserId(query.getUserId());
        ImChatId chatId = new ImChatId(query.getChatId());
        ImMessageToken messageToken = new ImMessageToken(query.getMessageToken());

        ImGroupChat groupChat = imGroupChatRepository.find(chatId);
        if (groupChat == null) {
            throw new NotFoundException("聊天不存在");
        }
        ImGroupMember groupMember = imGroupMemberRepository.find(groupChat.getGroupId(), userId);
        if (groupMember == null) {
            throw new BusinessException("无法查看别人的聊天记录");
        }

        ImGroupInboxMessage message = imGroupInboxMessageRepository.queryDetail(chatId, userId, messageToken);
        if (message == null) {
            throw new NotFoundException("消息不存在");
        }
        return ImMessageAppTransformer.INSTANCE.imGroupMessageDtoFrom(message);
    }
}
