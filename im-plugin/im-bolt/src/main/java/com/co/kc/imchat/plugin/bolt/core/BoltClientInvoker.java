package com.co.kc.imchat.plugin.bolt.core;

import com.alipay.remoting.rpc.RpcClient;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.model.BoltRequest;
import com.co.kc.imchat.plugin.bolt.model.BoltResponse;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;

public class BoltClientInvoker implements BoltInvoker {
    private final RpcClient rpcClient;

    public BoltClientInvoker(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    @Override
    public <T, R> R invoke(String address, String service, String operation, T request,
                           Class<R> responseType, int timeoutMillis) {
        try {
            String payload = JsonUtils.toJson(request);
            Object response = rpcClient.invokeSync(address, new BoltRequest(service, operation, payload), timeoutMillis);
            if (!(response instanceof BoltResponse boltResponse)) {
                throw new IllegalStateException("Unexpected Bolt response type: " + response);
            }
            if (!boltResponse.isSuccess()) {
                throw new IllegalStateException("Bolt response failed, code=" + boltResponse.getCode()
                        + ", message=" + boltResponse.getMessage());
            }
            if (responseType == Void.class) {
                return null;
            }
            return JsonUtils.fromJson(boltResponse.getData(), responseType);
        } catch (Exception ex) {
            throw new IllegalStateException("Bolt invocation failed: " + address + " " + service + "#" + operation, ex);
        }
    }
}
