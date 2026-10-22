package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 观察协议所属集群的身份值对象，不代表进程地址。
 */
public record BrokerClusterId(
        /* 不透明且不可为空的身份。 */
        String value) {
    public BrokerClusterId {
        AssertUtils.domainPropNotBlank("BrokerClusterId is required", value);
    }
}
