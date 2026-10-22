package com.co.kc.imchat.broker.sdk.model.params;

import com.co.kc.imchat.broker.sdk.access.BrokerDataPlaneRequest;
import com.co.kc.imchat.broker.sdk.model.dto.DrainGatewayWatermarkDTO;

import java.io.Serializable;
import java.util.List;

/** Leader 请求被排空 Broker 按 committed Gateway 水位只删除旧路由。 */
public record DrainRouteCleanupParams(
        String requesterBrokerId,
        String operationId,
        long oldEpoch,
        long newEpoch,
        long oldEventExpireAtMs,
        List<DrainGatewayWatermarkDTO> gateways) implements Serializable, BrokerDataPlaneRequest {

    public DrainRouteCleanupParams {
        if (requesterBrokerId == null || requesterBrokerId.isBlank()
                || operationId == null || operationId.isBlank()
                || oldEpoch < 0L || newEpoch <= oldEpoch || oldEventExpireAtMs <= 0L
                || gateways == null) {
            throw new IllegalArgumentException("invalid drain route cleanup request");
        }
        gateways = List.copyOf(gateways);
    }
}
