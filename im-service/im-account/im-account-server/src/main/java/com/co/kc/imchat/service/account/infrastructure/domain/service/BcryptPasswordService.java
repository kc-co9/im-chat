package com.co.kc.imchat.service.account.infrastructure.domain.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.co.kc.imchat.service.account.domain.user.model.UserPassword;
import com.co.kc.imchat.service.account.domain.user.model.UserRawPassword;
import com.co.kc.imchat.service.account.domain.user.service.PasswordService;

/**
 * @author kc
 */
public class BcryptPasswordService implements PasswordService {

    private static final BCrypt.Hasher HASHER = BCrypt.withDefaults();
    private static final BCrypt.Verifyer VERIFYER = BCrypt.verifyer();

    @Override
    public UserPassword encrypt(UserRawPassword rawPassword) {
        return new UserPassword(HASHER.hashToString(12, rawPassword.value().toCharArray()));
    }

    @Override
    public boolean verify(UserRawPassword rawPassword, UserPassword password) {
        return VERIFYER.verify(rawPassword.value().toCharArray(), password.value()).verified;
    }

}
