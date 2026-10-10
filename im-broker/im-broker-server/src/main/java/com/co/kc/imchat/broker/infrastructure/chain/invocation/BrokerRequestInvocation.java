package com.co.kc.imchat.broker.infrastructure.chain.invocation;

import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;

/**
 * 在基础设施请求链节点间传递已解码的请求、传输上下文及认证后的调用方。
 */
public record BrokerRequestInvocation<I>(
        BrokerBoltOperation operation,
        I request,
        BoltRequestContext context,
        BrokerCallerPrincipal principal) {

    public BrokerRequestInvocation {
        if (operation == null) {
            throw new IllegalArgumentException("broker operation must not be null");
        }
    }

    public static <I> BrokerRequestInvocation<I> initial(
            BrokerBoltOperation operation, I request, BoltRequestContext context) {
        return new BrokerRequestInvocation<>(operation, request, context, null);
    }

    public BrokerRequestInvocation<I> withPrincipal(BrokerCallerPrincipal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("authenticated principal must not be null");
        }
        return new BrokerRequestInvocation<>(operation, request, context, principal);
    }

}
