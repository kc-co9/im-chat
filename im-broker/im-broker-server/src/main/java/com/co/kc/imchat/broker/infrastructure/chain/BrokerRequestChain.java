package com.co.kc.imchat.broker.infrastructure.chain;

import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;

@FunctionalInterface
public interface BrokerRequestChain {

    Object proceed(BrokerRequestInvocation<?> invocation) throws Exception;
}
