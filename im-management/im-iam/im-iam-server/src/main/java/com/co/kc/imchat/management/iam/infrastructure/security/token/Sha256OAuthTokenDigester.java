package com.co.kc.imchat.management.iam.infrastructure.security.token;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.common.utils.HashUtils;

import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 对高熵 OAuth2 Token 生成不可逆 SHA-256 摘要。
 */
public class Sha256OAuthTokenDigester {
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    public String digest(String token) {
        AssertUtils.argNotBlank("token must not be blank", token);
        return HEX_FORMAT.formatHex(HashUtils.sha256(token));
    }

    public boolean matches(String token, String expectedDigest) {
        AssertUtils.argNotBlank("token must not be blank", token);
        AssertUtils.argNotBlank("token digest must not be blank", expectedDigest);
        try {
            return MessageDigest.isEqual(
                    HashUtils.sha256(token),
                    HEX_FORMAT.parseHex(expectedDigest));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
