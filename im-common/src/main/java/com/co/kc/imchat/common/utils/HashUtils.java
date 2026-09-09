package com.co.kc.imchat.common.utils;

import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;

/**
 * @author kc
 */
public class HashUtils {
    private HashUtils() {
    }

    public static String murmurHash32(String s) {
        return String.valueOf(Hashing.murmur3_32_fixed().hashString(s, StandardCharsets.UTF_8).asInt());
    }

    /**
     * 计算 UTF-8 文本的 SHA-256 摘要。
     *
     * @param value 原始文本
     * @return SHA-256 摘要字节
     */
    public static byte[] sha256(String value) {
        return Hashing.sha256().hashString(value, StandardCharsets.UTF_8).asBytes();
    }
}
