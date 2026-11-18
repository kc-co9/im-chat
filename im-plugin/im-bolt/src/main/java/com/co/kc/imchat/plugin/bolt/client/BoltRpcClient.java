package com.co.kc.imchat.plugin.bolt.client;

import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;

/**
 * Bolt RPC 调用客户端。
 * <p>
 * 面向业务模块封装远端地址、服务名、操作名和超时时间的通用调用入口。
 */
public class BoltRpcClient {
    /**
     * 执行统一 typed 调用的委托，不由本客户端管理其生命周期。
     */
    private final BoltInvoker boltInvoker;
    /**
     * 启动时固定的客户端目标准入策略快照。
     */
    private final ImBoltProperties.Client client;

    /**
     * 固定策略使用防御性快照；Invoker与原生客户端生命周期仍由装配拥有方管理。
     */
    public BoltRpcClient(BoltInvoker boltInvoker, ImBoltProperties.Client client) {
        this.boltInvoker = boltInvoker;
        this.client = client.snapshot();
    }

    /**
     * 固定启动策略验证目标后委托统一typed调用。
     * target 包含地址与身份，service/operation 指定操作，request 为请求数据；
     * responseType 指定返回类型，timeoutMillis 为调用总预算（毫秒），结果由 Invoker 返回。
     * 目标策略不匹配时抛出 LOCAL 调用异常，其他调用失败由 Invoker 传播。
     */
    public <T, R> R invoke(BoltTarget target, String service, String operation,
                           T request, Class<R> responseType, int timeoutMillis) {
        try {
            client.selectPeerUriPrefix(target);
        } catch (IllegalArgumentException failure) {
            throw new BoltInvocationException(BoltInvocationException.Failure.LOCAL, "Invalid Bolt invocation target policy", failure);
        }
        return boltInvoker.invoke(target, service, operation, request, responseType, timeoutMillis);
    }

}
