package com.co.kc.imchat.broker.infrastructure.chain;

import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;

/** Broker 请求责任链节点调用后续拦截器或最终 Handler 的抽象。 */
@FunctionalInterface
public interface BrokerRequestChain {

    Object proceed(BrokerRequestInvocation<?> invocation) throws Exception;
}
