package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.ImUserChatDescriptor;
import com.co.kc.imchat.support.exception.BusinessException;
import com.co.kc.imchat.support.exception.NotFoundException;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.chat.ImGroupChatRepository;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.group.ImGroupMember;
import com.co.kc.imchat.domain.group.ImGroupMemberRepository;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.domain.chat.ImPrivateChatRepository;
import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.message.ImGroupInboxMessage;
import com.co.kc.imchat.domain.message.ImGroupInboxMessageRepository;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.model.cqrs.command.chat.ImChatExitCmd;
import com.co.kc.imchat.model.cqrs.command.chat.ImGroupChatOpenCmd;
import com.co.kc.imchat.model.cqrs.command.chat.ImPrivateChatOpenCmd;
import com.co.kc.imchat.model.cqrs.dto.im.ImChatItemDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImChatOpenDTO;
import com.co.kc.imchat.model.cqrs.query.ImChatListQuery;
import com.co.kc.imchat.transformer.application.ImChatAppTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 聊天-应用服务
 */
@Slf4j
@RequiredArgsConstructor
public class ChatAppService {
    private final SnowflakeId snowflakeId;
    private final ImPrivateChatRepository imPrivateChatRepository;
    private final ImGroupChatRepository imGroupChatRepository;
    private final ImGroupMemberRepository imGroupMemberRepository;
    private final ImGroupInboxMessageRepository imGroupInboxMessageRepository;
    private final FriendRepository friendRepository;

    private final ImChatService imChatService;

    @Transactional(rollbackFor = Exception.class)
    public ImChatOpenDTO openPrivateChat(ImPrivateChatOpenCmd command) {
        UserId userId = new UserId(command.getUserId());
        UserId peerUserId = new UserId(command.getPeerUserId());

        Friend friend = friendRepository.find(userId, peerUserId);
        if (friend == null) {
            throw new NotFoundException("好友不存在");
        }

        ImPrivateChat userChat = imPrivateChatRepository.find(userId, peerUserId);
        if (userChat == null) {
            userChat = ImPrivateChat.builder()
                    .id(new ImChatId(snowflakeId.next()))
                    .userId(userId)
                    .peerUserId(peerUserId)
                    .type(ImChatType.PRIVATE)
                    .build();
            imPrivateChatRepository.save(userChat);
        }
        ImPrivateChat peerChat = imPrivateChatRepository.find(peerUserId, userId);
        if (peerChat == null) {
            peerChat = ImPrivateChat.builder()
                    .id(new ImChatId(snowflakeId.next()))
                    .userId(peerUserId)
                    .peerUserId(userId)
                    .type(ImChatType.PRIVATE)
                    .build();
            imPrivateChatRepository.save(peerChat);
        }

        imChatService.enterChat(userChat);
        return new ImChatOpenDTO(userChat.getId().getValue());
    }

    @Transactional(rollbackFor = Exception.class)
    public ImChatOpenDTO openGroupChat(ImGroupChatOpenCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());

        ImGroupChat groupChat = imGroupChatRepository.find(chatId);
        if (groupChat == null) {
            throw new NotFoundException("聊天不存在");
        }
        if (!groupChat.getUserId().equals(userId)) {
            throw new BusinessException("用户无此群聊权限");
        }
        ImGroupMember member = imGroupMemberRepository.find(groupChat.getGroupId(), userId);
        if (member == null) {
            throw new BusinessException("用户无此群聊权限");
        }
        groupChat.readToLatest();
        imGroupChatRepository.save(groupChat);

        imChatService.enterChat(groupChat);
        List<ImGroupInboxMessage> unreadMessages = imGroupInboxMessageRepository.findUnreadMessages(groupChat.getId(), userId);
        for (ImGroupInboxMessage message : unreadMessages) {
            message.read(userId);
        }
        imGroupInboxMessageRepository.saveAll(unreadMessages);
        return new ImChatOpenDTO(groupChat.getId().getValue());
    }

    public void exitChat(ImChatExitCmd command) {
        UserId userId = new UserId(command.getUserId());
        imChatService.exitChat(userId);
    }

    public List<ImChatItemDTO> getChatList(ImChatListQuery query) {
        UserId userId = new UserId(query.getUserId());
        List<ImUserChatDescriptor> imUserChatDescriptors = imChatService.getUserChatList(userId);
        return ImChatAppTransformer.INSTANCE.imChatListFrom(imUserChatDescriptors);
    }

}
