package com.co.kc.imchat.plugin.bolt.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.net.URI;

/**
 * 地址与独立提供的完整预期身份，不携带调用方可变信任策略。
 */
public record BoltTarget(String address, String expectedPeerIdentity) {
    public BoltTarget {
        AssertUtils.argNotBlank("Bolt target address is required", address);
        AssertUtils.argNotBlank("Bolt target identity is required", expectedPeerIdentity);
        URI identity = URI.create(expectedPeerIdentity);
        AssertUtils.argTrue("Bolt target identity must be an absolute URI without query or fragment",
                identity.isAbsolute() && identity.getRawQuery() == null && identity.getRawFragment() == null);
    }
}
