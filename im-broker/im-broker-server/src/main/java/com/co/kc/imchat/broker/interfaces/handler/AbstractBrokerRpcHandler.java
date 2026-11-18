package com.co.kc.imchat.broker.interfaces.handler;

import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.common.utils.ReflectUtils;

/**
 * Broker RPC 业务处理器，声明 operation 与请求类型，作为责任链最后一个节点的业务动作。
 * 原始 Handler 不注册为 Bolt 入口，也不持有责任链。
 *
 * @param <I> RPC 入参类型
 * @param <O> RPC 出参类型
 */
public abstract class AbstractBrokerRpcHandler<I, O> {
    private final BrokerBoltOperation operation;

    protected AbstractBrokerRpcHandler(BrokerBoltOperation operation) {
        this.operation = operation;
    }

    public final BrokerBoltOperation brokerOperation() {
        return operation;
    }

    public String service() {
        return operation.service().service();
    }

    public String operation() {
        return operation.operation();
    }

    protected abstract Object process(I params) throws Exception;

    protected Object invoke(I request, BrokerCallerPrincipal principal) throws Exception {
        return process(request);
    }

    /** 仅供已组装的责任链最后一个节点执行解码后的业务请求。 */
    public final Object handleRequest(Object request, BrokerCallerPrincipal principal) throws Exception {
        return invoke(requestType().cast(request), principal);
    }

    @SuppressWarnings("unchecked")
    public final Class<I> requestType() {
        Class<?> type = ReflectUtils.resolveGenericTypeArgument(getClass(), AbstractBrokerRpcHandler.class, 0);
        if (type == null) {
            throw new IllegalStateException("failed to resolve broker rpc request type: " + getClass().getName());
        }
        return (Class<I>) type;
    }
}
