package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * 集群 operation 进入失败、降级或强制终态时保留的稳定原因值对象。
 *
 * <p>它记录状态机为何没有按正常路径完成，例如路由证据缺失、节点不可达或人工批准了
 * 残留风险；该对象用于操作历史和后续恢复判断，不承载异常对象或基础设施调用细节。</p>
 */
public record OperationFailureReason(String value) implements Serializable {

    public OperationFailureReason {
        AssertUtils.domainPropNotBlank("operation failure reason is required", value);
    }
}
