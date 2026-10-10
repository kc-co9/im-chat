package com.co.kc.imchat.common.utils;

import com.google.common.hash.Hashing;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

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

    /**
     * 计算原始字节的 SHA-256 摘要。
     */
    public static byte[] sha256(byte[] value) {
        return Hashing.sha256().hashBytes(value).asBytes();
    }

    /**
     * 计算 UTF-8 文本的 SHA-256 十六进制摘要。
     *
     * @param value 原始文本
     * @return 小写 SHA-256 十六进制摘要
     */
    public static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    /**
     * 按“元素数量 + big-endian long 序列”的固定编码计算 SHA-256 十六进制摘要。
     *
     * @param values 有序 long 集合
     * @return 小写 SHA-256 十六进制摘要
     */
    public static String sha256HexLongs(List<Long> values) {
        if (values == null || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("values must not contain null");
        }
        ByteBuffer bytes = ByteBuffer.allocate(Long.BYTES * (values.size() + 1));
        bytes.putLong(values.size());
        values.forEach(bytes::putLong);
        return HexFormat.of().formatHex(sha256(bytes.array()));
    }
}
