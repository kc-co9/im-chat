package com.co.kc.imchat.domain.user;

import com.co.kc.imchat.support.exception.AuthException;
import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.support.auth.PasswordService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordService passwordService;

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
        return false;
    }
}
