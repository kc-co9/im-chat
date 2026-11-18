package com.co.kc.imchat.broker.domain.authority.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * Lease 申请被拒绝或被 fence 时的稳定原因值对象。
 *
 * <p>它用于在控制面、Broker 节点和管理诊断之间传递可解释的拒绝事实；它不是异常类型，
 * 不应包含调用栈、远端协议对象或只对某个基础设施实现有意义的内部细节。</p>
 */
public record LeaseRejectionReason(String value) implements Serializable {

    public LeaseRejectionReason {
        AssertUtils.domainPropNotBlank("lease rejection reason is required", value);
    }
}
