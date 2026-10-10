package com.co.kc.imchat.broker.domain.authority.model;

import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 标识一次 quorum 读取请求的上下文，用于关联读取证据，不代表集群操作或 Lease 的身份。
 * 租约读取复用 prepare nonce，操作恢复复用 operationId，独立读取可以使用新生成的标识。
 */
public record QuorumReadNonce(/* 关联本次 quorum 读取与返回证据的请求上下文。 */ String value) {
    public QuorumReadNonce {
        AssertUtils.domainPropNotBlank("quorum read nonce is required", value);
    }

    /** 将租约准备申请的 nonce 用作本次读取上下文，不改变其内容。 */
    public static QuorumReadNonce forLease(LeaseNonce nonce) {
        return new QuorumReadNonce(nonce.value());
    }

    /** 将已登记操作标识用作恢复读取的上下文，不改变操作身份。 */
    public static QuorumReadNonce forOperation(ClusterOperationId operationId) {
        return new QuorumReadNonce(operationId.value());
    }
}
