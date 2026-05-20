package com.co.kc.imchat.domain.user;

import com.co.kc.imchat.domain.session.Session;
import com.co.kc.imchat.domain.session.SessionRepository;
import com.co.kc.imchat.domain.session.SessionStatus;
import com.co.kc.imchat.support.exception.AuthException;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImPrivateChat;
import com.co.kc.imchat.support.auth.PasswordService;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
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
                .orElseThrow(() -> new AuthException("user is not exist"));

        boolean hasPassed = user.validateRawPassword(rawPassword, passwordService);
        if (!hasPassed) {
            throw new AuthException("user is failed to authenticate");
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
