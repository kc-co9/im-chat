package com.co.kc.imchat.broker.adapter.cluster.raft;

import com.co.kc.imchat.common.utils.JsonUtils;

import java.nio.charset.StandardCharsets;

/**
 * BrokerClusterSnapshot 的版本化持久化编码。
 */
public final class BrokerClusterSnapshotCodec {

    private BrokerClusterSnapshotCodec() {
    }

    public static byte[] encode(BrokerClusterSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        return JsonUtils.toJson(snapshot).getBytes(StandardCharsets.UTF_8);
    }

    public static BrokerClusterSnapshot decode(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("snapshot content must not be empty");
        }
        return JsonUtils.fromJson(new String(content, StandardCharsets.UTF_8),
                BrokerClusterSnapshot.class);
    }
}
