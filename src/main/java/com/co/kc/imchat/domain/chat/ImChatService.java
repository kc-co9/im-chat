package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.common.utils.FunctionUtils;
import com.co.kc.imchat.domain.message.ImMessageId;
import com.co.kc.imchat.domain.session.Session;
import com.co.kc.imchat.domain.session.SessionRepository;
import com.co.kc.imchat.domain.shared.NickName;
import com.co.kc.imchat.domain.user.User;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * IM聊天-领域服务
 */
@RequiredArgsConstructor
public class ImChatService {
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;

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

    public List<ImGroupMember> newGroupMembers(List<UserId> memberIds) {
        List<User> users = userRepository.find(memberIds);
        return FunctionUtils.mappingList(users, user ->
                new ImGroupMember(user.getId(), new NickName(user.getUsername().getValue())));
    }


}
