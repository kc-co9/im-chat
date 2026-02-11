package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.friend.Friend;
import com.co.kc.imchat.domain.friend.FriendRepository;
import com.co.kc.imchat.domain.message.ImMessage;
import com.co.kc.imchat.support.exception.AuthException;
import com.co.kc.imchat.support.utils.FunctionUtils;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.session.Session;
import com.co.kc.imchat.domain.session.SessionRepository;
import com.co.kc.imchat.domain.user.UserId;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * IM聊天-领域服务
 */
@RequiredArgsConstructor
public class ImChatService {
    private final FriendRepository friendRepository;
    private final ImChatRepository imChatRepository;
    private final SessionRepository sessionRepository;

    public List<ImUserChatDescriptor> getUserChatList(UserId userId) {
        List<ImPrivateChat> imPrivateChatList = imChatRepository.findPrivateChatList(userId);

        List<ImUserChatDescriptor> imUserChatDescriptors = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(imPrivateChatList)) {
            List<UserId> friendUserIds = FunctionUtils.mappingList(imPrivateChatList, o -> o.getAnother(userId));
            List<Friend> friendList = friendRepository.find(userId, friendUserIds);
            Map<UserId, Friend> friendMap = FunctionUtils.mappingMap(friendList, Friend::getFriendUserId, Function.identity());

            List<ImChatId> chatIds = FunctionUtils.mappingList(imPrivateChatList, ImPrivateChat::getId);
            List<ImMessage> chatLastMessages = imChatRepository.findLastMessageList(ImChatType.PRIVATE, chatIds);
            Map<ImChatId, ImMessage> chatLastMessageMap = FunctionUtils.mappingMap(chatLastMessages, ImMessage::getChatId, Function.identity());

            List<ImUserChatDescriptor> imPrivateChatDescriptors = imPrivateChatList.stream()
                    .map(imPrivateChat -> {
                        UserId friendUserId = imPrivateChat.getAnother(userId);
                        ImUserChatDescriptor descriptor = new ImUserChatDescriptor();
                        descriptor.setChatId(imPrivateChat.getId());
                        descriptor.setChatName(obtainFriendChatName(friendMap.get(friendUserId)));
                        descriptor.setChatType(imPrivateChat.getType());
                        descriptor.setChatLastMessage(chatLastMessageMap.get(imPrivateChat.getId()));
                        return descriptor;
                    }).collect(Collectors.toList());
            imUserChatDescriptors.addAll(imPrivateChatDescriptors);
        }

        List<ImGroupChat> imGroupChatList = imChatRepository.findGroupChatList(userId);
        if (CollectionUtils.isNotEmpty(imGroupChatList)) {
            List<ImChatId> chatIds = FunctionUtils.mappingList(imGroupChatList, ImGroupChat::getId);
            List<ImGroupMember> imGroupMembers = imChatRepository.findUserGroupMemberList(userId, chatIds);
            Map<ImChatId, ImGroupAlias> imGroupAliasMap =
                    FunctionUtils.mappingMap(imGroupMembers, ImGroupMember::getChatId, ImGroupMember::getGroupAlias);

            List<ImMessage> chatLastMessages = imChatRepository.findLastMessageList(ImChatType.GROUP, chatIds);
            Map<ImChatId, ImMessage> chatLastMessageMap = FunctionUtils.mappingMap(chatLastMessages, ImMessage::getChatId, Function.identity());

            List<ImUserChatDescriptor> imGroupChatDescriptors = imGroupChatList.stream().map(imGroupChat -> {
                ImUserChatDescriptor descriptor = new ImUserChatDescriptor();
                descriptor.setChatId(imGroupChat.getId());
                descriptor.setChatName(obtainGroupChatName(imGroupChat, imGroupAliasMap.get(imGroupChat.getId())));
                descriptor.setChatType(imGroupChat.getType());
                descriptor.setChatLastMessage(chatLastMessageMap.get(imGroupChat.getId()));
                return descriptor;
            }).collect(Collectors.toList());
            imUserChatDescriptors.addAll(imGroupChatDescriptors);
        }

        return imUserChatDescriptors;
    }

    public void enterChat(ImChatId chatId, UserId userId) {
        Session session = sessionRepository.find(userId);
        if (session == null || !session.isSignIn()) {
            throw new AuthException("用户尚未登陆");
        }

        session.onEnterChat(chatId);
        sessionRepository.save(session);
    }

    public void exitChat(UserId userId) {
        Session session = sessionRepository.find(userId);
        if (session == null || !session.isSignIn()) {
            throw new AuthException("用户尚未登陆");
        }

        session.onExitChat();
        sessionRepository.save(session);
    }

    /**
     * 增加未读消息数
     *
     * @param chatId     聊天ID
     * @param receiverId 接收者ID
     */
    public void increaseUnreadCount(ImChatId chatId, UserId receiverId) {

    }

    /**
     * 减少未读消息数
     *
     * @param chatId    聊天ID
     * @param userId    用户ID
     * @param messageId 消息ID
     */
    public void decreaseUnreadCount(ImChatId chatId, UserId userId, ImMessageId messageId) {

    }

    /**
     * 获取好友的聊天名称
     *
     * @param friend 好友
     * @return 聊天名称
     */
    private ImChatName obtainFriendChatName(Friend friend) {
        if (friend == null) {
            return null;
        }
        if (friend.getFriendAlias() != null) {
            return new ImChatName(friend.getFriendAlias().getValue());
        } else {
            return new ImChatName(friend.getFriendName().getValue());
        }
    }

    private ImChatName obtainGroupChatName(ImGroupChat imGroupChat, ImGroupAlias imGroupAlias) {
        if (imGroupChat == null) {
            return null;
        }
        if (imGroupAlias != null) {
            return new ImChatName(imGroupAlias.getValue());
        } else {
            return new ImChatName(imGroupChat.getName().getValue());
        }
    }


}
