package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 关联单轮观察请求及响应的身份值对象，不用于 Lease 或 quorum 授权。
 */
public record BrokerObservationNonce(
        /* 不透明且不可为空的身份。 */
        String value) {
    public BrokerObservationNonce {
        AssertUtils.domainPropNotBlank("BrokerObservationNonce is required", value);
    }
}
