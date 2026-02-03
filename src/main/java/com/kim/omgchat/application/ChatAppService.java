package com.kim.omgchat.application;

import com.kim.omgchat.common.exception.AuthException;
import com.kim.omgchat.common.exception.NotFoundException;
import com.kim.omgchat.common.identity.snowflake.SnowflakeId;
import com.kim.omgchat.common.utils.FunctionUtils;
import com.kim.omgchat.domain.chat.ImChat;
import com.kim.omgchat.domain.chat.ImChatId;
import com.kim.omgchat.domain.chat.ImChatName;
import com.kim.omgchat.domain.chat.ImChatRepository;
import com.kim.omgchat.domain.chat.ImChatService;
import com.kim.omgchat.domain.chat.ImChatType;
import com.kim.omgchat.domain.chat.ImGroupChat;
import com.kim.omgchat.domain.chat.ImGroupMember;
import com.kim.omgchat.domain.chat.ImPrivateChat;
import com.kim.omgchat.domain.chat.ImPrivatePair;
import com.kim.omgchat.domain.friend.Friend;
import com.kim.omgchat.domain.friend.FriendRepository;
import com.kim.omgchat.domain.session.Session;
import com.kim.omgchat.domain.session.SessionRepository;
import com.kim.omgchat.domain.user.UserId;
import com.kim.omgchat.model.cqrs.command.im.ImGroupChatCreateCmd;
import com.kim.omgchat.model.cqrs.command.im.ImPrivateChatCreateCmd;
import com.kim.omgchat.model.cqrs.command.im.ImChatEnterCmd;
import com.kim.omgchat.model.cqrs.command.im.ImChatLeaveCmd;
import com.kim.omgchat.model.cqrs.dto.im.ImChatCreateDTO;
import com.kim.omgchat.model.cqrs.dto.im.ImChatItemDTO;
import com.kim.omgchat.model.cqrs.query.ImChatListQuery;
import com.kim.omgchat.transformer.ImChatAppTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 聊天-应用服务
 */
@Slf4j
@RequiredArgsConstructor
public class ChatAppService {
    private final SnowflakeId snowflakeId;
    private final ImChatRepository imChatRepository;
    private final FriendRepository friendRepository;
    private final SessionRepository sessionRepository;

    private final ImChatService imChatService;

    public ImChatCreateDTO startPrivateChat(ImPrivateChatCreateCmd command) {
        UserId senderId = new UserId(command.getSenderId());
        UserId receiverId = new UserId(command.getReceiverId());

        Friend friend = friendRepository.find(senderId, receiverId);
        if (friend == null) {
            throw new NotFoundException("好友不存在");
        }

        ImPrivatePair pair = new ImPrivatePair(senderId, receiverId);
        ImPrivateChat imPrivateChat = imChatRepository.find(pair);
        if (imPrivateChat == null) {
            imPrivateChat = new ImPrivateChat();
            imPrivateChat.setId(new ImChatId(snowflakeId.next()));
            imPrivateChat.setPair(pair);
            imPrivateChat.setName(null);
            imPrivateChat.setType(ImChatType.PRIVATE);
            imChatRepository.save(imPrivateChat);
        }

        return new ImChatCreateDTO(imPrivateChat.getId().getValue());
    }

    public ImChatCreateDTO startGroupChat(ImGroupChatCreateCmd command) {
        UserId ownerId = new UserId(command.getOwnerId());
        List<UserId> memberIds = FunctionUtils.mappingList(command.getMemberIds(), UserId::new);
        ImChatName imChatName = new ImChatName(command.getGroupName());

        ImChatId chatId = new ImChatId(snowflakeId.next());
        List<ImGroupMember> groupMembers = imChatService.newGroupMembers(memberIds);

        ImGroupChat imGroupChat = new ImGroupChat();
        imGroupChat.setId(chatId);
        imGroupChat.setName(imChatName);
        imGroupChat.setOwnerId(ownerId);
        imGroupChat.setMembers(groupMembers);
        imGroupChat.setType(ImChatType.GROUP);
        imChatRepository.save(imGroupChat);

        return new ImChatCreateDTO(imGroupChat.getId().getValue());
    }

    public void enterChat(ImChatEnterCmd command) {
        UserId userId = new UserId(command.getUserId());
        ImChatId chatId = new ImChatId(command.getChatId());

        Session session = sessionRepository.find(userId);
        if (session == null) {
            throw new NotFoundException("用户会话不存在");
        }
        if (session.isSignIn()) {
            throw new AuthException("用户尚未登陆");
        }

        session.onEnterChat(chatId);
        sessionRepository.save(session);
    }

    public void leaveChat(ImChatLeaveCmd command) {
        UserId userId = new UserId(command.getUserId());

        Session session = sessionRepository.find(userId);
        if (session == null) {
            throw new NotFoundException("用户会话不存在");
        }
        if (session.isSignIn()) {
            throw new AuthException("用户尚未登陆");
        }

        session.onLeaveChat();
        sessionRepository.save(session);
    }

    public List<ImChatItemDTO> getChatList(ImChatListQuery query) {
        UserId userId = new UserId(query.getUserId());
        List<ImChat> chatList = imChatRepository.find(userId);
        return ImChatAppTransformer.INSTANCE.imChatListFrom(chatList);
    }

}
