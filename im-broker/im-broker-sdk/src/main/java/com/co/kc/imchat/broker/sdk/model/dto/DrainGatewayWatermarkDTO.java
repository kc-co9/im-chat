package com.co.kc.imchat.broker.sdk.model.dto;

import java.io.Serializable;

/** committed drain operation 中单个 Gateway 的确认水位。 */
public record DrainGatewayWatermarkDTO(
        String gatewayId,
        long routeRevision,
        long routeCount,
        String contentChecksum) implements Serializable {

    public DrainGatewayWatermarkDTO {
        if (gatewayId == null || gatewayId.isBlank() || routeRevision < 0L
                || routeCount < 0L || contentChecksum == null
                || contentChecksum.isBlank()) {
            throw new IllegalArgumentException("invalid drain Gateway watermark");
        }
    }
}
