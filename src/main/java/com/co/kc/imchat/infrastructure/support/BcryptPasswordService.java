package com.co.kc.imchat.infrastructure.support;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.co.kc.imchat.domain.user.UserPassword;
import com.co.kc.imchat.domain.user.UserRawPassword;
import com.co.kc.imchat.support.auth.PasswordService;
import org.springframework.stereotype.Service;

/**
 * @author kc
 */
@Service
public class BcryptPasswordService implements PasswordService {

    private static final BCrypt.Hasher HASHER = BCrypt.withDefaults();
    private static final BCrypt.Verifyer VERIFYER = BCrypt.verifyer();

    @Override
    public UserPassword encrypt(UserRawPassword rawPassword) {
        return new UserPassword(HASHER.hashToString(12, rawPassword.getValue().toCharArray()));
    }

    @Override
    public boolean verify(UserRawPassword rawPassword, UserPassword password) {
        return VERIFYER.verify(rawPassword.getValue().toCharArray(), password.getValue()).verified;
    }

}
