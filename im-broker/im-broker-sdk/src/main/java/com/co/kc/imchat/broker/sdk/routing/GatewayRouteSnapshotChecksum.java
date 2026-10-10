package com.co.kc.imchat.broker.sdk.routing;

import com.co.kc.imchat.common.utils.HashUtils;

import java.util.List;

/** GatewayRouteSnapshot 完整有序 userId 集合的稳定 SHA-256 校验和。 */
public final class GatewayRouteSnapshotChecksum {
    private GatewayRouteSnapshotChecksum() {
    }

    public static String calculate(List<Long> userIds) {
        return HashUtils.sha256HexLongs(userIds);
    }
}
