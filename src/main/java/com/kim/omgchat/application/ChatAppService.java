package com.kim.omgchat.application;

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
import com.kim.omgchat.domain.user.UserId;
import com.kim.omgchat.model.cqrs.command.chat.ImChatExitCmd;
import com.kim.omgchat.model.cqrs.command.chat.ImGroupChatCreateCmd;
import com.kim.omgchat.model.cqrs.command.chat.ImGroupChatEnterCmd;
import com.kim.omgchat.model.cqrs.command.chat.ImPrivateChatEnterCmd;
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

    private final ImChatService imChatService;

    public ImChatCreateDTO enterPrivateChat(ImPrivateChatEnterCmd command) {
        UserId senderId = new UserId(command.getSenderId());
        UserId receiverId = new UserId(command.getReceiverId());

        Friend friend = friendRepository.find(senderId, receiverId);
        if (friend == null) {
            throw new NotFoundException("好友不存在");
        }

        ImPrivatePair pair = new ImPrivatePair(senderId, receiverId);
        ImPrivateChat imPrivateChat = imChatRepository.findPrivateChat(pair);
        if (imPrivateChat == null) {
            imPrivateChat = new ImPrivateChat();
            imPrivateChat.setId(new ImChatId(snowflakeId.next()));
            imPrivateChat.setPair(pair);
            imPrivateChat.setName(null);
            imPrivateChat.setType(ImChatType.PRIVATE);
            imChatRepository.save(imPrivateChat);
        }

        imChatService.enterChat(imPrivateChat.getId(), senderId);

        return new ImChatCreateDTO(imPrivateChat.getId().getValue());
    }

    public ImChatCreateDTO createGroupChat(ImGroupChatCreateCmd command) {
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

    public void enterGroupChat(ImGroupChatEnterCmd command) {
        ImChatId chatId = new ImChatId(command.getChatId());
        UserId userId = new UserId(command.getUserId());

        imChatService.enterChat(chatId, userId);
    }

    public void exitChat(ImChatExitCmd command) {
        UserId userId = new UserId(command.getUserId());
        imChatService.exitChat(userId);
    }

    public List<ImChatItemDTO> getChatList(ImChatListQuery query) {
        UserId userId = new UserId(query.getUserId());
        List<ImChat> chatList = imChatRepository.find(userId);
        return ImChatAppTransformer.INSTANCE.imChatListFrom(chatList);
    }

}
