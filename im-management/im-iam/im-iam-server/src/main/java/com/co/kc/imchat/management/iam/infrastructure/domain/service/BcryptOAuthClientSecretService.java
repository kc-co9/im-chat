package com.co.kc.imchat.management.iam.infrastructure.domain.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientSecret;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthRawClientSecret;
import com.co.kc.imchat.management.iam.domain.application.service.OAuthClientSecretService;

/** BCrypt OAuth2 客户端密钥编码实现。 */
public class BcryptOAuthClientSecretService implements OAuthClientSecretService {
    private static final int HASH_COST = 12;
    private static final BCrypt.Hasher HASHER = BCrypt.withDefaults();

    @Override
    public OAuthClientSecret encode(OAuthRawClientSecret secret) {
        return new OAuthClientSecret(
                HASHER.hashToString(HASH_COST, secret.value().toCharArray()));
    }
}
