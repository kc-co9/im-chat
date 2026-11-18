package com.co.kc.imchat.broker.infrastructure.chain;

import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerHandlerInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerRequestInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;

import java.util.List;

/**
 * 按装配顺序执行一个 operation 的全部节点，业务 Handler 是最后的节点。
 * 每次请求独立传递节点位置，前置节点可环绕后续真实业务调用。
 */
public final class BrokerRequestInterceptorChain implements BoltRequestHandler {
    private final BrokerBoltOperation operation;
    private final Class<?> requestType;
    private final List<BrokerRequestInterceptor> interceptors;

    public BrokerRequestInterceptorChain(List<BrokerRequestInterceptor> interceptors) {
        if (!(interceptors.getLast() instanceof BrokerHandlerInterceptor business)) {
            throw new IllegalArgumentException("broker handler must be the last request node");
        }
        this.operation = business.handler().brokerOperation();
        this.requestType = business.handler().requestType();
        this.interceptors = List.copyOf(interceptors);
    }

    @Override
    public String service() {
        return operation.service().service();
    }

    @Override
    public String operation() {
        return operation.operation();
    }

    @Override
    public Object handle(BoltRequestContext context, String payload) throws Exception {
        Object request = JsonUtils.fromJson(payload, requestType);
        return proceed(0, BrokerRequestInvocation.initial(operation, request, context));
    }

    private Object proceed(int index, BrokerRequestInvocation<?> invocation) throws Exception {
        BrokerRequestInterceptor interceptor = interceptors.get(index);
        return interceptor.intercept(invocation, chain -> proceed(index + 1, chain));
    }
}
