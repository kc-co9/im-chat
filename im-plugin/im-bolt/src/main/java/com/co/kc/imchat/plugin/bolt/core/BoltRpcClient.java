package com.co.kc.imchat.plugin.bolt.core;

import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;

/**
 * Bolt RPC 调用客户端。
 * <p>
 * 面向业务模块封装远端地址、服务名、操作名和超时时间的通用调用入口。
 */
public class BoltRpcClient {
    private final BoltInvoker boltInvoker;

    public BoltRpcClient(BoltInvoker boltInvoker) {
        this.boltInvoker = boltInvoker;
    }

    public <T, R> R invoke(String address, String service, String operation,
                           T request, Class<R> responseType, int timeoutMillis) {
        return boltInvoker.invoke(address, service, operation, request, responseType, timeoutMillis);
    }
}
