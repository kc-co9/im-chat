package com.co.kc.imchat.management.iam.sdk.session.crypto;

/** 应用 Session 中 OAuth2 凭据的认证加密边界。 */
public interface IamSessionCipher {
    String encrypt(String value);

    String decrypt(String encryptedValue);
}
