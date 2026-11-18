package com.co.kc.imchat.management.ops.model.io;

/** CLI 提交结果；Leader 跟随地址仅供服务端编排使用。 */
public record OpsCliSubmissionResponse(
        /* 操作唯一身份。 */
        String operationId,
        /* 提交处理状态。 */
        String status,
        /* 接受操作时的 epoch。 */
        Long acceptedEpoch,
        /* 目标配置生效后的 epoch。 */
        Long targetEpoch,
        /* 面向 CLI 的处理说明。 */
        String message) {
}
