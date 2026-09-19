package com.co.kc.imchat.broker.support.client;

import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import com.co.kc.imchat.gateway.ws.sdk.model.params.GatewayFrameWriteParams;
import com.co.kc.imchat.gateway.ws.sdk.model.result.GatewayFrameWriteResult;
import com.co.kc.imchat.plugin.metrics.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GatewayFrameWriter {
    private final GatewayClient gatewayClient;

    @Observed(name = "im.broker.gateway.frame.write")
    public GatewayFrameWriteResult writeFrame(
            GatewayEndpointDTO gateway,
            GatewayFrameWriteParams params
    ) {
        return gatewayClient.writeFrame(gateway, params);
    }
}
