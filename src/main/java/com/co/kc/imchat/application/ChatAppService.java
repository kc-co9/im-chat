package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.ImUserChatDescriptor;
import com.co.kc.imchat.domain.friend.FriendService;
import com.co.kc.imchat.domain.group.Group;
import com.co.kc.imchat.domain.group.GroupAlias;
import com.co.kc.imchat.domain.group.GroupRepository;
import com.co.kc.imchat.domain.group.GroupService;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImGroupChatRepository;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.domain.chat.ImPrivateChatRepository;
import com.co.kc.imchat.domain.message.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.ImGroupInboxMessageRepository;
import com.co.kc.imchat.domain.message.ImMessageService;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.model.cqrs.command.chat.ImChatExitCmd;
import com.co.kc.imchat.model.cqrs.command.chat.GroupAliasChangeCmd;
import com.co.kc.imchat.model.cqrs.command.chat.ImGroupChatOpenCmd;
import com.co.kc.imchat.model.cqrs.command.chat.ImPrivateChatOpenCmd;
import com.co.kc.imchat.model.cqrs.command.chat.PrivateChatHideCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupChatHideCmd;
import com.co.kc.imchat.model.cqrs.dto.im.ImChatItemDTO;
import com.co.kc.imchat.model.cqrs.dto.group.GroupChatOpenDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImPrivateChatOpenDTO;
import com.co.kc.imchat.model.cqrs.query.ImChatListQuery;
import com.co.kc.imchat.support.exception.NotFoundException;
import com.co.kc.imchat.support.lock.DistributeLockScene;
import com.co.kc.imchat.support.lock.annotation.DistributeLock;
import com.co.kc.imchat.transformer.application.ImChatAppTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天-应用服务
 */
@Slf4j
@RequiredArgsConstructor
public class ChatAppService {
    private final ImPrivateChatRepository imPrivateChatRepository;
    private final ImGroupChatRepository imGroupChatRepository;
    private final GroupRepository groupRepository;
    private final GroupService groupService;
    private final ImGroupInboxMessageRepository imGroupInboxMessageRepository;
    private final ImChatService imChatService;
    private final FriendService friendService;
    private final ImMessageService imMessageService;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    @DistributeLock(scene = DistributeLockScene.PRIVATE_CHAT_OPEN, key = "#LockKeys.userPair(#command.userId, #command.peerUserId)")
    public ImPrivateChatOpenDTO openPrivateChat(ImPrivateChatOpenCmd command) {
        UserId userId = new UserId(command.getUserId());
        UserId peerUserId = new UserId(command.getPeerUserId());

        friendService.ensureFriendshipActive(userId, peerUserId);

        ImPrivateChat userChat = imPrivateChatRepository.find(userId, peerUserId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        userChat.activate(LocalDateTime.now());
        imPrivateChatRepository.save(userChat);

        imChatService.enterChat(userChat);

        return new ImPrivateChatOpenDTO(userChat.getId().getValue(), peerUserId.getValue());
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public GroupChatOpenDTO openGroupChat(ImGroupChatOpenCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());

        ImGroupChat groupChat = imGroupChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(groupChat, userId);

        Group group = groupRepository.find(groupChat.getGroupId()).orElseThrow(() -> new NotFoundException("群组不存在"));
        groupService.ensureGroupMember(group, userId);

        groupChat.activate(LocalDateTime.now());
        groupChat.readToLatest();
        imGroupChatRepository.save(groupChat);

        List<ImGroupInboxMessage> unreadMessages = imMessageService.readUnreadGroupMessages(groupChat, userId);
        imGroupInboxMessageRepository.save(unreadMessages);

        imChatService.enterChat(groupChat);

        return new GroupChatOpenDTO(groupChat.getId().getValue(), groupChat.getGroupId().getValue());
    }

    public void exitChat(ImChatExitCmd command) {
        UserId userId = new UserId(command.getUserId());
        imChatService.exitChat(userId);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void hidePrivateChat(PrivateChatHideCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());

        ImPrivateChat privateChat = imPrivateChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(privateChat, userId);

        privateChat.hide();
        imPrivateChatRepository.save(privateChat);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void hideGroupChat(GroupChatHideCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());

        ImGroupChat groupChat = imGroupChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(groupChat, userId);

        Group group = groupRepository.find(groupChat.getGroupId()).orElseThrow(() -> new NotFoundException("群组不存在"));
        groupService.ensureGroupMember(group, userId);

        groupChat.hide();
        imGroupChatRepository.save(groupChat);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void changeGroupAlias(GroupAliasChangeCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());
        GroupAlias groupAlias = new GroupAlias(command.getGroupAlias());

        ImGroupChat groupChat = imGroupChatRepository.find(chatId).orElseThrow(() -> new NotFoundException("聊天不存在"));
        imChatService.ensureBelongsTo(groupChat, userId);

        Group group = groupRepository.find(groupChat.getGroupId()).orElseThrow(() -> new NotFoundException("群组不存在"));
        groupService.ensureGroupMember(group, userId);

        groupChat.changeGroupAlias(groupAlias);
        imGroupChatRepository.save(groupChat);
    }

    public List<ImChatItemDTO> getChatList(ImChatListQuery query) {
        UserId userId = new UserId(query.getUserId());
        List<ImUserChatDescriptor> imUserChatDescriptors = imChatService.getUserChatList(userId);
        return ImChatAppTransformer.INSTANCE.imChatListFrom(imUserChatDescriptors);
    }

}
