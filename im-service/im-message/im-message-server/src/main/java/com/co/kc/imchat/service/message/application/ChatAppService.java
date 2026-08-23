package com.co.kc.imchat.service.message.application;

import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.common.domain.group.model.GroupAlias;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.message.domain.chat.model.GroupChatJoin;
import com.co.kc.imchat.service.message.domain.chat.model.GroupChatMembership;
import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.service.message.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.service.message.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.service.message.domain.chat.model.ImUserChatDescriptor;
import com.co.kc.imchat.service.message.domain.chat.repository.ImGroupChatRepository;
import com.co.kc.imchat.service.message.domain.chat.repository.ImPrivateChatRepository;
import com.co.kc.imchat.service.message.domain.chat.service.ImChatService;
import com.co.kc.imchat.service.message.domain.message.event.ImGroupMessageSentEvent;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupInboxMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupMessageTransmission;
import com.co.kc.imchat.service.message.domain.message.model.ImMessage;
import com.co.kc.imchat.service.message.domain.message.repository.ImGroupInboxMessageRepository;
import com.co.kc.imchat.service.message.domain.message.service.ImMessageService;
import com.co.kc.imchat.service.message.domain.chat.repository.ImChatViewRepository;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatView;
import com.co.kc.imchat.service.message.facade.dto.UserGroupChatSummaryDTO;
import com.co.kc.imchat.service.message.facade.params.UserGroupChatSummaryGetParams;
import com.co.kc.imchat.service.message.facade.params.GroupChatMemberRemoveParams;
import com.co.kc.imchat.service.message.facade.params.GroupChatsCreateParams;
import com.co.kc.imchat.service.message.facade.params.GroupChatsDismissParams;
import com.co.kc.imchat.service.message.facade.params.GroupChatsJoinParams;
import com.co.kc.imchat.service.message.facade.params.PrivateChatPrepareParams;
import com.co.kc.imchat.service.message.facade.params.PrivateChatRemoveParams;
import com.co.kc.imchat.service.message.facade.params.UserGroupChatSummariesGetParams;
import com.co.kc.imchat.service.message.model.cqrs.command.chat.GroupAliasChangeCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.chat.ImGroupChatOpenCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.chat.ImPrivateChatOpenCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.chat.PrivateChatHideCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.chat.ChatExitCmd;
import com.co.kc.imchat.service.message.model.cqrs.command.group.GroupChatHideCmd;
import com.co.kc.imchat.service.message.model.cqrs.dto.im.ImChatItemDTO;
import com.co.kc.imchat.service.message.model.cqrs.dto.group.GroupChatOpenDTO;
import com.co.kc.imchat.service.message.model.cqrs.dto.im.ImPrivateChatOpenDTO;
import com.co.kc.imchat.service.message.model.cqrs.query.ImChatListQuery;
import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.service.message.application.lock.ImMessageLockScene;
import com.co.kc.imchat.plugin.lock.annotation.DistributeLock;
import com.co.kc.imchat.service.message.transformer.ImChatAppTransformer;
import com.co.kc.imchat.service.message.domain.social.model.FriendDisplay;
import com.co.kc.imchat.service.message.domain.social.model.GroupSummary;
import com.co.kc.imchat.service.message.adapter.social.SocialAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 聊天-应用服务
 */
@Slf4j
@RequiredArgsConstructor
public class ChatAppService {
    private final ImPrivateChatRepository imPrivateChatRepository;
    private final ImGroupChatRepository imGroupChatRepository;
    private final ImGroupInboxMessageRepository imGroupInboxMessageRepository;
    private final ImChatService imChatService;
    private final SocialAdapter socialAdapter;
    private final ImMessageService imMessageService;
    private final DomainEventPublisher eventPublisher;
    private final ImChatViewRepository imChatViewRepository;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    @DistributeLock(scene = ImMessageLockScene.PRIVATE_CHAT_OPEN, key = "#LockKeys.userPair(#command.userId(), #command.peerUserId())")
    public ImPrivateChatOpenDTO openPrivateChat(ImPrivateChatOpenCmd command) {
        UserId userId = new UserId(command.userId());
        UserId peerUserId = new UserId(command.peerUserId());

        socialAdapter.ensureFriendshipActive(userId.value(), peerUserId.value());

        ImPrivateChat userChat = imPrivateChatRepository.find(userId, peerUserId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        userChat.activate(LocalDateTime.now());
        userChat.readToLatest();
        imPrivateChatRepository.save(userChat);
        imChatViewRepository.save(new ImChatView(userId, userChat.getId()));

        return ImChatAppTransformer.INSTANCE.imPrivateChatOpenDtoFrom(userChat, peerUserId);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public GroupChatOpenDTO openGroupChat(ImGroupChatOpenCmd command) {
        UserId userId = new UserId(command.userId());
        ImChatId chatId = new ImChatId(command.chatId());

        ImGroupChat groupChat = imGroupChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(groupChat, userId);

        socialAdapter.ensureGroupMember(groupChat.getGroupId().value(), userId.value());

        groupChat.activate(LocalDateTime.now());
        groupChat.readToLatest();
        imGroupChatRepository.save(groupChat);

        List<ImGroupInboxMessage> unreadMessages = imMessageService.readUnreadGroupMessages(groupChat, userId);
        imGroupInboxMessageRepository.save(unreadMessages);
        imChatViewRepository.save(new ImChatView(userId, chatId));

        return ImChatAppTransformer.INSTANCE.groupChatOpenDtoFrom(groupChat);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void hidePrivateChat(PrivateChatHideCmd command) {
        UserId userId = new UserId(command.userId());
        ImChatId chatId = new ImChatId(command.chatId());

        ImPrivateChat privateChat = imPrivateChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(privateChat, userId);

        privateChat.hide();
        imPrivateChatRepository.save(privateChat);
        imChatViewRepository.clear(userId);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void hideGroupChat(GroupChatHideCmd command) {
        UserId userId = new UserId(command.userId());
        ImChatId chatId = new ImChatId(command.chatId());

        ImGroupChat groupChat = imGroupChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(groupChat, userId);

        socialAdapter.ensureGroupMember(groupChat.getGroupId().value(), userId.value());

        groupChat.hide();
        imGroupChatRepository.save(groupChat);
        imChatViewRepository.clear(userId);
    }

    /**
     * 离开当前聊天详情，但不隐藏聊天列表项。
     */
    public void exitChat(ChatExitCmd command) {
        UserId userId = new UserId(command.userId());
        imChatViewRepository.clear(userId);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void changeGroupAlias(GroupAliasChangeCmd command) {
        UserId userId = new UserId(command.userId());
        ImChatId chatId = new ImChatId(command.chatId());
        GroupAlias groupAlias = new GroupAlias(command.groupAlias());

        ImGroupChat groupChat = imGroupChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(groupChat, userId);

        socialAdapter.ensureGroupMember(groupChat.getGroupId().value(), userId.value());

        groupChat.changeGroupAlias(groupAlias);
        imGroupChatRepository.save(groupChat);
    }

    public List<ImChatItemDTO> getChatList(ImChatListQuery query) {
        UserId userId = new UserId(query.userId());
        List<ImUserChatDescriptor> imUserChatDescriptors = getUserChatList(userId);
        return ImChatAppTransformer.INSTANCE.imChatListFrom(imUserChatDescriptors);
    }

    public UserGroupChatSummaryDTO getUserGroupChatSummary(UserGroupChatSummaryGetParams params) {
        return imGroupChatRepository.find(new GroupId(params.groupId()), new UserId(params.userId()))
                .map(ImChatAppTransformer.INSTANCE::userGroupChatSummaryDtoFrom)
                .orElseGet(UserGroupChatSummaryDTO::empty);
    }

    public List<UserGroupChatSummaryDTO> getUserGroupChatSummaries(UserGroupChatSummariesGetParams params) {
        Set<GroupId> groupIds = CollectionUtils.emptyIfNull(params.groupIds()).stream()
                .map(GroupId::new)
                .collect(Collectors.toSet());
        return imGroupChatRepository.find(new UserId(params.userId()), groupIds)
                .stream()
                .map(ImChatAppTransformer.INSTANCE::userGroupChatSummaryDtoFrom)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void preparePrivateChat(PrivateChatPrepareParams params) {
        UserId userId = new UserId(params.userId());
        UserId friendUserId = new UserId(params.friendUserId());
        imChatService.prepareHiddenPrivateChat(userId, friendUserId)
                .ifPresent(imPrivateChatRepository::save);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void removePrivateChat(PrivateChatRemoveParams params) {
        imPrivateChatRepository.remove(new UserId(params.userId()), new UserId(params.friendUserId()));
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void createGroupChats(GroupChatsCreateParams params) {
        UserId ownerId = new UserId(params.ownerId());
        GroupId groupId = new GroupId(params.groupId());
        GroupChatMembership chatMembership = imChatService.createGroupChatMembership(
                ImChatAppTransformer.INSTANCE.groupChatMembersFrom(groupId, params.members()));
        ImGroupMessageTransmission transmission = imMessageService.transmitGroupCreated(ownerId, chatMembership);
        saveGroupTransmission(groupId, ownerId, transmission);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void dismissGroupChats(GroupChatsDismissParams params) {
        UserId ownerId = new UserId(params.ownerId());
        GroupId groupId = new GroupId(params.groupId());
        GroupChatMembership chatMembership = imChatService.findGroupChatMembership(groupId);
        ImGroupMessageTransmission transmission = imMessageService.transmitGroupDismissed(ownerId, chatMembership);
        saveGroupTransmission(groupId, ownerId, transmission);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void joinGroupChats(GroupChatsJoinParams params) {
        UserId inviterId = new UserId(params.inviterId());
        GroupId groupId = new GroupId(params.groupId());
        GroupChatJoin chatJoin = imChatService.joinGroupChat(
                groupId,
                ImChatAppTransformer.INSTANCE.groupChatMembersFrom(groupId, params.members()));
        ImGroupMessageTransmission transmission = imMessageService.transmitGroupMemberJoined(
                inviterId,
                chatJoin.describeChatMembership(),
                ImChatAppTransformer.INSTANCE.memberDescriptorsFrom(params.memberDescriptors()));
        saveGroupTransmission(groupId, inviterId, transmission);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void removeGroupChatMember(GroupChatMemberRemoveParams params) {
        imGroupChatRepository.remove(new GroupId(params.groupId()), new UserId(params.userId()));
    }

    private List<ImUserChatDescriptor> getUserChatList(UserId userId) {
        List<ImPrivateChat> privateChats = imPrivateChatRepository.find(userId);
        List<ImUserChatDescriptor> privateChatDescriptors = buildPrivateChatDescriptors(userId, privateChats);

        List<ImGroupChat> groupChats = imGroupChatRepository.find(userId);
        List<ImUserChatDescriptor> groupChatDescriptors = buildGroupChatDescriptors(userId, groupChats);

        return imChatService.mergeChatDescriptors(privateChatDescriptors, groupChatDescriptors);
    }

    private List<ImUserChatDescriptor> buildPrivateChatDescriptors(UserId userId, List<ImPrivateChat> privateChats) {
        List<ImPrivateChat> visibleChats = imChatService.visiblePrivateChats(privateChats);
        if (CollectionUtils.isEmpty(visibleChats)) {
            return Collections.emptyList();
        }

        List<Long> friendUserIds = visibleChats.stream()
                .map(chat -> chat.getPeerUserId().value())
                .toList();
        Map<Long, FriendDisplay> friendMap = socialAdapter.getFriendDisplays(userId.value(), friendUserIds)
                .stream()
                .collect(Collectors.toMap(FriendDisplay::userId, Function.identity(), (left, right) -> left));

        List<ImChatId> chatIds = FunctionUtils.mappingList(visibleChats, ImPrivateChat::getId);
        List<ImMessage> chatLastMessages = imPrivateChatRepository.findLastMessageList(chatIds, userId);
        Map<Long, ImMessage> chatLastMessageMap = FunctionUtils.mappingMap(
                chatLastMessages, message -> message.getId().value(), Function.identity());

        return imChatService.describePrivateChats(visibleChats, friendMap, chatLastMessageMap);
    }

    private List<ImUserChatDescriptor> buildGroupChatDescriptors(UserId userId, List<ImGroupChat> groupChats) {
        List<ImGroupChat> visibleChats = imChatService.visibleGroupChats(groupChats);
        if (CollectionUtils.isEmpty(visibleChats)) {
            return Collections.emptyList();
        }

        List<Long> groupIds = visibleChats.stream()
                .map(chat -> chat.getGroupId().value())
                .toList();
        Map<Long, GroupSummary> groupMap = socialAdapter.getGroupSummaries(groupIds)
                .stream()
                .collect(Collectors.toMap(GroupSummary::groupId, Function.identity(), (left, right) -> left));

        List<ImChatId> chatIds = FunctionUtils.mappingList(visibleChats, ImGroupChat::getId);
        List<ImMessage> chatLastMessages = imGroupChatRepository.findLastMessageList(chatIds, userId);
        Map<ImChatId, ImMessage> chatLastMessageMap = FunctionUtils.mappingMap(
                chatLastMessages, message -> ((ImGroupInboxMessage) message).getChatId(), Function.identity());

        return imChatService.describeGroupChats(visibleChats, groupMap, chatLastMessageMap);
    }

    private void saveGroupTransmission(GroupId groupId, UserId senderId, ImGroupMessageTransmission transmission) {
        imGroupInboxMessageRepository.save(transmission.inboxMessages());
        imGroupChatRepository.save(transmission.groupChats());
        ImGroupMessageSentEvent event =
                imMessageService.newImMessageSentEvent(groupId, transmission.getSenderMessage(senderId));
        eventPublisher.publish(event);
    }

}
