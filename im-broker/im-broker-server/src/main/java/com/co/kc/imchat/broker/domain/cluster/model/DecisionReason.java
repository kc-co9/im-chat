package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * 聚合或领域服务非成功决定的稳定原因值对象。
 *
 * <p>该原因用于解释冲突、拒绝或暂不可用，不表示 Java 异常堆栈。它会随领域结果返回给
 * 应用层，并可被共识重放、管理接口和诊断记录稳定识别。</p>
 */
public record DecisionReason(String value) implements Serializable {

    public DecisionReason {
        AssertUtils.domainPropNotBlank("decision reason is required", value);
    }
}
