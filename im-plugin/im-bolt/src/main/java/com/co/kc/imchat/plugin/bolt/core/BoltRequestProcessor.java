package com.co.kc.imchat.plugin.bolt.core;

import com.alipay.remoting.BizContext;
import com.alipay.remoting.rpc.protocol.SyncUserProcessor;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.model.BoltRequest;
import com.co.kc.imchat.plugin.bolt.model.BoltResponse;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BoltRequestProcessor extends SyncUserProcessor<BoltRequest> {
    private final Map<String, BoltRequestHandler> handlers;

    public BoltRequestProcessor(List<BoltRequestHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(handler -> key(handler.service(), handler.operation()),
                        Function.identity()));
    }

    @Override
    public Object handleRequest(BizContext bizContext, BoltRequest request) {
        BoltRequestHandler handler = handlers.get(key(request.service(), request.operation()));
        if (handler == null) {
            return BoltResponse.failed("BOLT_HANDLER_NOT_FOUND", "Bolt handler not found");
        }
        try {
            Object response = handler.handle(request.payload());
            return BoltResponse.ok(JsonUtils.toJson(response));
        } catch (Exception ex) {
            return BoltResponse.failed("BOLT_HANDLER_ERROR", ex.getMessage());
        }
    }

    @Override
    public String interest() {
        return BoltRequest.class.getName();
    }

    private String key(String service, String operation) {
        return service + "#" + operation;
    }
}
