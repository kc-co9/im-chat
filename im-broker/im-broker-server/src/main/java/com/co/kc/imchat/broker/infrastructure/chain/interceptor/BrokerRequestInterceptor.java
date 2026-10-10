package com.co.kc.imchat.broker.infrastructure.chain.interceptor;

import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestChain;
import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;

public interface BrokerRequestInterceptor {

    Object intercept(BrokerRequestInvocation<?> invocation, BrokerRequestChain chain) throws Exception;
}
