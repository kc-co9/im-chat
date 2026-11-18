package com.co.kc.imchat.broker.model.io;


/** 管理端可见的集群操作状态与路由恢复摘要，不暴露幂等摘要或成员端点。 */
public record BrokerClusterOperationResponse(
        /* 操作身份。 */ String operationId,
        /* 操作类型。 */ String type,
        /* 目标 Broker 身份。 */ String targetBrokerId,
        /* 当前提交阶段。 */ String phase,
        /* 接收操作时的路由纪元。 */ Long acceptedEpoch,
        /* 操作应用后的路由纪元。 */ Long resultEpoch,
        /* 失败或降级原因，无原因时为空。 */ String error,
        /* 操作登记时间。 */ Long createdAtMs,
        /* 操作完成时间，未终结时为空。 */ Long completedAtMs,
        /* 冻结的 Gateway 数量；非路由恢复操作为空。 */ Integer gatewayCount,
        /* 已确认路由重建的 Gateway 数量；非路由恢复操作为空。 */ Integer confirmedGatewayCount,
        /* 已重建路由数量；尚无汇总证据时为空。 */ Long rebuiltRouteCount,
        /* 旧 Broker 残留路由数量；尚无汇总证据时为空。 */ Long residualOldRouteCount) {
}
