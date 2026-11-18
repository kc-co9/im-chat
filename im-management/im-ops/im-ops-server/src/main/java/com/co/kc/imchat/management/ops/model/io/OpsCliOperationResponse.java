package com.co.kc.imchat.management.ops.model.io;

/** CLI operation 查询的窄投影；UNKNOWN 不表示未提交。 */
public record OpsCliOperationResponse(
        /* 操作唯一身份。 */
        String operationId,
        /* 操作类型。 */
        String type,
        /* 目标 Broker。 */
        String targetBrokerId,
        /* 当前生命周期阶段。 */
        String phase,
        /* 接受操作时的 epoch。 */
        Long acceptedEpoch,
        /* 完成操作时的 epoch。 */
        Long resultEpoch,
        /* 操作失败说明。 */
        String error,
        /* 创建时间，Unix 毫秒。 */
        Long createdAtMs,
        /* 完成时间，Unix 毫秒。 */
        Long completedAtMs) {
}
