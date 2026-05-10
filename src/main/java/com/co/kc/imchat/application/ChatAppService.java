package com.co.kc.imchat.application;

import com.co.kc.imchat.domain.chat.ImChatName;
import com.co.kc.imchat.domain.chat.ImGroupMemberId;
import com.co.kc.imchat.domain.chat.ImGroupName;
import com.co.kc.imchat.domain.chat.ImUserChatDescriptor;
import com.co.kc.imchat.model.cqrs.command.chat.ImPrivateChatCreateCmd;
import com.co.kc.imchat.model.cqrs.dto.im.ImPrivateChatEnterDTO;
import com.co.kc.imchat.support.exception.BusinessException;
import com.co.kc.imchat.support.exception.NotFoundException;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.support.utils.FunctionUtils;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatService;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.chat.ImGroupChatRepository;
import com.co.kc.imchat.domain.chat.ImGroupMember;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.domain.chat.ImPrivateChatRepository;
import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.model.cqrs.command.chat.ImChatExitCmd;
import com.co.kc.imchat.model.cqrs.command.chat.ImGroupChatCreateCmd;
import com.co.kc.imchat.model.cqrs.command.chat.ImGroupChatEnterCmd;
import com.co.kc.imchat.model.cqrs.command.chat.ImPrivateChatEnterCmd;
import com.co.kc.imchat.model.cqrs.dto.im.ImChatCreateDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImChatItemDTO;
import com.co.kc.imchat.model.cqrs.query.ImChatListQuery;
import com.co.kc.imchat.transformer.application.ImChatAppTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天-应用服务
 */
@Slf4j
@RequiredArgsConstructor
public class ChatAppService {
    private final SnowflakeId snowflakeId;
    private final ImPrivateChatRepository imPrivateChatRepository;
    private final ImGroupChatRepository imGroupChatRepository;
    private final FriendRepository friendRepository;

    private final ImChatService imChatService;

    @Transactional(rollbackFor = Exception.class)
    public ImChatCreateDTO createPrivateChat(ImPrivateChatCreateCmd command) {
        UserId senderId = new UserId(command.getSenderId());
        UserId receiverId = new UserId(command.getReceiverId());

        Friend friend = friendRepository.find(senderId, receiverId);
        if (friend == null) {
            throw new NotFoundException("好友不存在");
        }

        ImPrivateChat senderChat = imPrivateChatRepository.find(senderId, receiverId);
        if (senderChat == null) {
            senderChat = ImPrivateChat.builder()
                    .id(new ImChatId(snowflakeId.next()))
                    .userId(senderId)
                    .peerUserId(receiverId)
                    .type(ImChatType.PRIVATE)
                    .build();
            imPrivateChatRepository.save(senderChat);
        }
        ImPrivateChat receiverChat = imPrivateChatRepository.find(receiverId, senderId);
        if (receiverChat == null) {
            receiverChat = ImPrivateChat.builder()
                    .id(new ImChatId(snowflakeId.next()))
                    .userId(receiverId)
                    .peerUserId(senderId)
                    .type(ImChatType.PRIVATE)
                    .build();
            imPrivateChatRepository.save(receiverChat);
        }

        return new ImChatCreateDTO(senderChat.getId().getValue());
    }

    public ImPrivateChatEnterDTO enterPrivateChat(ImPrivateChatEnterCmd command) {
        ImChatId chatId = new ImChatId(command.getChatId());
        UserId userId = new UserId(command.getUserId());

        ImPrivateChat imPrivateChat = imPrivateChatRepository.find(chatId);
        if (imPrivateChat == null) {
            throw new NotFoundException("聊天不存在");
        }
        if (!imPrivateChat.contain(userId)) {
            throw new BusinessException("用户无此聊天权限");
        }

        imChatService.enterChat(chatId, userId);

        Friend friend = friendRepository.find(userId, imPrivateChat.getPeerUserId());
        ImChatName chatName = imChatService.obtainFriendChatName(friend);

        return new ImPrivateChatEnterDTO(
                chatId.getValue(), chatName.getValue(), friend.getFriendUserId().getValue(), friend.displayName().getValue());
    }

    public ImChatCreateDTO createGroupChat(ImGroupChatCreateCmd command) {
        UserId ownerId = new UserId(command.getOwnerId());
        ImGroupName groupName = new ImGroupName(command.getGroupName());
        List<UserId> memberIds = FunctionUtils.mappingList(command.getMemberIds(), UserId::new);

        ImChatId chatId = new ImChatId(snowflakeId.next());

        ImGroupChat imGroupChat = new ImGroupChat();
        imGroupChat.setId(chatId);
        imGroupChat.setName(groupName);
        imGroupChat.setOwnerId(ownerId);
        imGroupChat.setType(ImChatType.GROUP);
        imGroupChatRepository.save(imGroupChat);

        List<ImGroupMember> imGroupMembers = memberIds.stream()
                .map(userId -> {
                    ImGroupMember imGroupMember = new ImGroupMember();
                    imGroupMember.setId(new ImGroupMemberId(chatId, userId));
                    imGroupMember.setUserId(userId);
                    imGroupMember.setChatId(chatId);
                    imGroupMember.setGroupAlias(null);
                    imGroupMember.setUserAlias(null);
                    return imGroupMember;
                }).collect(Collectors.toList());
        imGroupChatRepository.saveGroupMembers(imGroupMembers);

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
        List<ImUserChatDescriptor> imUserChatDescriptors = imChatService.getUserChatList(userId);
        return ImChatAppTransformer.INSTANCE.imChatListFrom(imUserChatDescriptors);
    }

}
