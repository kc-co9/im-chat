package com.co.kc.imchat.broker.sdk.model.result;

import java.io.Serializable;

/** 被排空 Broker 的旧路由 cleanup 摘要。 */
public record DrainRouteCleanupResult(
        Status status,
        long removedOldRouteCount,
        long residualOldRouteCount,
        boolean checksumsMatch,
        String message) implements Serializable {

    public enum Status {
        CLEANED,
        REJECTED
    }

    public DrainRouteCleanupResult {
        if (status == null || removedOldRouteCount < 0L || residualOldRouteCount < 0L) {
            throw new IllegalArgumentException("invalid drain route cleanup result");
        }
    }

    /** 清理请求不符合已提交排空证据，未执行本轮删除。 */
    public static DrainRouteCleanupResult rejected(long residualRouteCount) {
        return new DrainRouteCleanupResult(Status.REJECTED, 0L, residualRouteCount, false,
                "cleanup request does not match committed drain evidence");
    }
}
