package com.co.kc.imchat.broker.sdk.routing;

import com.co.kc.imchat.common.utils.HashUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** 以固定大端编码和 SHA-256 生成路由算法使用的用户哈希及用户与 Broker 的匹配分值。 */
final class RouteBrokerHashing {

    private RouteBrokerHashing() {
    }

    static long score(long userId, String brokerId) {
        byte[] brokerBytes = brokerId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer input = ByteBuffer.allocate(Long.BYTES + Integer.BYTES + brokerBytes.length)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(userId)
                .putInt(brokerBytes.length)
                .put(brokerBytes);
        return firstLong(sha256(input.array()));
    }

    static long userHash(long userId) {
        ByteBuffer input = ByteBuffer.allocate(Long.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(userId);
        return firstLong(sha256(input.array()));
    }

    private static byte[] sha256(byte[] input) {
        return HashUtils.sha256(input);
    }

    private static long firstLong(byte[] digest) {
        return ByteBuffer.wrap(digest).order(ByteOrder.BIG_ENDIAN).getLong();
    }
}
