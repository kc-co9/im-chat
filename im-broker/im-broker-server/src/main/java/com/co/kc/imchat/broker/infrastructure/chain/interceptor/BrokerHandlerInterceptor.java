package com.co.kc.imchat.broker.infrastructure.chain.interceptor;

import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestChain;
import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;

/**
 * 请求链的业务节点，调用对应 operation 的 Handler，执行后不再继续传递。
 */
public record BrokerHandlerInterceptor(AbstractBrokerRpcHandler<?, ?> handler) implements BrokerRequestInterceptor {

    @Override
    public Object intercept(BrokerRequestInvocation<?> invocation, BrokerRequestChain chain) throws Exception {
        return handler.handleRequest(invocation.request(), invocation.principal());
    }
}
