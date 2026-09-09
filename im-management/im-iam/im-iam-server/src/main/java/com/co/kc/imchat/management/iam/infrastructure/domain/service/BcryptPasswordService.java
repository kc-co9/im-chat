package com.co.kc.imchat.management.iam.infrastructure.domain.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorPassword;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorRawPassword;
import com.co.kc.imchat.management.iam.domain.administrator.service.PasswordService;

/**
 * BCrypt 管理员密码编码实现。
 */
public class BcryptPasswordService implements PasswordService {
    private static final int HASH_COST = 12;
    private static final BCrypt.Hasher HASHER = BCrypt.withDefaults();
    private static final BCrypt.Verifyer VERIFYER = BCrypt.verifyer();
    private static final byte[] UNKNOWN_ACCOUNT_HASH =
            "$2a$12$woSp/V83XIRb7yHWNWfk6uaCp3pHVc.bwXX/s6S0SSSHdre1HdXmW"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Override
    public AdministratorPassword encrypt(AdministratorRawPassword password) {
        return new AdministratorPassword(
                HASHER.hashToString(HASH_COST, password.value().toCharArray()));
    }

    @Override
    public boolean verify(
            AdministratorRawPassword password,
            AdministratorPassword encryptedPassword
    ) {
        return VERIFYER.verify(password.value().toCharArray(), encryptedPassword.value()).verified;
    }

    @Override
    public void verifyUnknown(AdministratorRawPassword password) {
        VERIFYER.verify(password.value().toCharArray(), UNKNOWN_ACCOUNT_HASH);
    }
}
