package com.co.kc.imchat.domain.user;

import com.co.kc.imchat.domain.session.Session;
import com.co.kc.imchat.domain.session.SessionRepository;
import com.co.kc.imchat.domain.session.SessionStatus;
import com.co.kc.imchat.support.exception.AuthException;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.support.auth.PasswordService;
import com.co.kc.imchat.support.identity.snowflake.SnowflakeId;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserService {

    private final SnowflakeId snowflakeId;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordService passwordService;

    public User newUser(UserEmail email, UserName username, UserRawPassword rawPassword) {
        return new User(new UserId(snowflakeId.next()), email, username, passwordService.encrypt(rawPassword));
    }

    public User authenticate(UserEmail email, UserRawPassword rawPassword) {
        User user = userRepository.find(email);
        if (user == null) {
            throw new AuthException("user is not exist");
        }

        boolean hasPassed = user.validateRawPassword(rawPassword, passwordService);
        if (!hasPassed) {
            throw new AuthException("user is failed to authenticate");
        }

        return user;
    }

    public boolean isChatting(ImChatId chatId, UserId receiverId) {
        Session session = sessionRepository.find(receiverId);
        if (session == null) {
            return false;
        }
        return SessionStatus.ONLINE.equals(session.getStatus()) && chatId.equals(session.getChatId());
    }
}
