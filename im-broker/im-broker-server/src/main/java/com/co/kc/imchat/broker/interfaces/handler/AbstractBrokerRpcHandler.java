package com.co.kc.imchat.broker.interfaces.handler;

import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltService;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.common.utils.ReflectUtils;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;

/**
 * Broker Bolt RPC 处理器基类。
 * <p>
 * 统一完成 RPC service、operation 和请求反序列化，具体 handler 只处理对应 operation 的业务动作。
 *
 * @param <I> RPC 入参类型
 * @param <O> RPC 出参类型
 */
public abstract class AbstractBrokerRpcHandler<I, O> implements BoltRequestHandler {
    private final BrokerBoltService service;
    private final BrokerBoltOperation operation;

    protected AbstractBrokerRpcHandler(BrokerBoltOperation operation) {
        this(operation.service(), operation);
    }

    protected AbstractBrokerRpcHandler(BrokerBoltService service, BrokerBoltOperation operation) {
        this.service = service;
        this.operation = operation;
    }

    @Override
    public String service() {
        return service.service();
    }

    @Override
    public String operation() {
        return operation.operation();
    }

    @Override
    public O handle(String payload) throws Exception {
        return process(JsonUtils.fromJson(payload, resolveRequestType()));
    }

    protected abstract O process(I params);

    @SuppressWarnings("unchecked")
    private Class<I> resolveRequestType() {
        Class<?> type = ReflectUtils.resolveGenericTypeArgument(getClass(), AbstractBrokerRpcHandler.class, 0);
        if (type == null) {
            throw new IllegalStateException("failed to resolve broker rpc request type: " + getClass().getName());
        }
        return (Class<I>) type;
    }
}
