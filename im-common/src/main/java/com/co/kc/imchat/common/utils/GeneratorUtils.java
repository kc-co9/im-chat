package com.co.kc.imchat.common.utils;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * 生成器
 *
 * @author kc
 */
public class GeneratorUtils {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private GeneratorUtils() {
    }

    /**
     * 生成UUID字符串
     *
     * @return UUID
     */
    public static String nextUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成使用 URL Safe Base64 编码的安全随机标识。
     *
     * @param byteLength 随机字节数
     * @return 无 Padding 的随机标识
     */
    public static String nextRandomId(int byteLength) {
        if (byteLength <= 0) {
            throw new IllegalArgumentException("byteLength must be positive");
        }
        byte[] value = new byte[byteLength];
        SECURE_RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
