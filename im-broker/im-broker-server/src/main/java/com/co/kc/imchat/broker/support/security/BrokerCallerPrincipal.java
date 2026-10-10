package com.co.kc.imchat.broker.support.security;

import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.common.utils.AssertUtils;

/** 从受信 Bolt 上下文识别出的请求调用方及其角色。 */
public record BrokerCallerPrincipal(
        /* 受信传输层解析出的调用方身份。 */
        String callerIdentity,
        /* 经入口认证的调用方角色。 */
        BrokerCallerRole callerRole) {

    public BrokerCallerPrincipal {
        AssertUtils.argNotBlank("Broker caller identity is required", callerIdentity);
        AssertUtils.argNotNull("Broker caller callerRole is required", callerRole);
    }
}
