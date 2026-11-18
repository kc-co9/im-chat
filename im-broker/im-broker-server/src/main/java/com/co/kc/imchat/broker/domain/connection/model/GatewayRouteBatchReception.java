package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.Optional;

/**
 * Gateway 路由分片的接收事实；只有 COMPLETE 携带完整且摘要正确的批次，不表示路由已应用。
 */
public record GatewayRouteBatchReception(
        /* 本轮分片接收状态。 */ Status status,
        /* 可以在 Lease 保护下应用的完整批次。 */ Optional<GatewayRouteBatch> batch) {
    public GatewayRouteBatchReception {
        AssertUtils.allDomainPropNotNull("route batch reception is incomplete", status, batch);
        AssertUtils.domainPropTrue("complete reception must carry its batch",
                (status == Status.COMPLETE) == batch.isPresent());
    }

    public enum Status {
        CHUNK_ACCEPTED, COMPLETE, INVALID, CAPACITY_EXCEEDED
    }
}
