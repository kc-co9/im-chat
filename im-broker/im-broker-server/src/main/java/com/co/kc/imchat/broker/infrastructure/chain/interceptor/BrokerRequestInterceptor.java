package com.co.kc.imchat.broker.infrastructure.chain.interceptor;

import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestChain;
import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;

/** Broker 请求责任链节点契约，可在推进后续链之前校验请求或环绕后续业务执行。 */
public interface BrokerRequestInterceptor {

    Object intercept(BrokerRequestInvocation<?> invocation, BrokerRequestChain chain) throws Exception;
}
