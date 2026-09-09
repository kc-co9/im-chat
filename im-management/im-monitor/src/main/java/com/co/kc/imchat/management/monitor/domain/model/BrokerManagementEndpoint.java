package com.co.kc.imchat.management.monitor.domain.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.net.URI;

/**
 * 服务发现中的 Broker 管理端点；旧节点缺少 metadata 时保留节点身份并标记为不支持。
 */
public record BrokerManagementEndpoint(String brokerId, URI baseUri, String errorSummary) {
    public BrokerManagementEndpoint {
        AssertUtils.argNotBlank("brokerId must not be blank", brokerId);
        AssertUtils.argTrue(
                "baseUri or errorSummary must be present",
                baseUri != null || errorSummary != null && !errorSummary.isBlank());
    }

    public boolean isSupported() {
        return baseUri != null;
    }
}
