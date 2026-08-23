package com.co.kc.imchat.plugin.session.token.codec;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.plugin.session.properties.JwtProperties;
import io.jsonwebtoken.SignatureAlgorithm;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;

/**
 * 创建 JWT 签名密钥，并集中处理本地临时密钥策略。
 */
public final class JwtSigningKeyFactory {
    /**
     * 根据 JWT 配置创建签名密钥。
     *
     * @param properties JWT 配置
     * @return 签名密钥
     */
    public Key create(JwtProperties properties) {
        AssertUtils.argNotNull("jwt properties must not be null", properties);
        byte[] secret = resolveSecret(properties);
        return new SecretKeySpec(secret, SignatureAlgorithm.HS512.getJcaName());
    }

    private byte[] resolveSecret(JwtProperties properties) {
        if (properties.hasSecret()) {
            return properties.getSecret().getBytes(StandardCharsets.UTF_8);
        }
        throw new IllegalStateException(
                "im.session.jwt.secret is required when JWT is enabled");
    }

}
