package com.co.kc.imchat.domain.user.service;

import com.co.kc.imchat.domain.session.repository.SessionRepository;
import com.co.kc.imchat.domain.session.model.SessionStatus;
import com.co.kc.imchat.domain.user.model.User;
import com.co.kc.imchat.domain.user.model.UserEmail;
import com.co.kc.imchat.domain.user.model.UserId;
import com.co.kc.imchat.domain.user.model.UserName;
import com.co.kc.imchat.domain.user.model.UserRawPassword;
import com.co.kc.imchat.domain.user.repository.UserRepository;
import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.domain.chat.model.ImChatId;
import com.co.kc.imchat.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.common.identity.snowflake.SnowflakeId;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordService passwordService;
    private final SnowflakeId snowflakeId;

    public User newUser(UserEmail email, UserName username, UserRawPassword rawPassword) {
        return new User(new UserId(snowflakeId.next()), email, username, passwordService.encrypt(rawPassword));
    }

    public User authenticate(UserEmail email, UserRawPassword rawPassword) {
        User user = userRepository.find(email)
                .orElseThrow(() -> new AuthException("用户不存在"));

        boolean hasPassed = user.validateRawPassword(rawPassword, passwordService);
        if (!hasPassed) {
            throw new AuthException("用户认证失败");
        }

        return user;
    }

    public boolean isChatting(ImChatId chatId, UserId receiverId) {
        return sessionRepository.find(receiverId)
                .map(session -> SessionStatus.ONLINE.equals(session.getStatus()) && chatId.equals(session.getChatId()))
                .orElse(false);
    }

    public boolean isOnline(UserId userId) {
        return sessionRepository.find(userId)
                .map(session -> SessionStatus.ONLINE.equals(session.getStatus()))
                .orElse(false);
    }

    /**
     * 会话归属用户是否正在该私聊会话界面（用于未读等策略）。
     */
    public boolean isChatting(ImPrivateChat chat) {
        return isChatting(chat.getId(), chat.getUserId());
    }
}
