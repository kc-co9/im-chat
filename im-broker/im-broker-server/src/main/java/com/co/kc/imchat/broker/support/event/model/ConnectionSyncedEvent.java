package com.co.kc.imchat.broker.support.event.model;

import com.co.kc.imchat.broker.sdk.model.dto.UserGatewayDTO;

import java.time.LocalDateTime;
import java.util.List;

public record ConnectionSyncedEvent(String gatewayId,
                                    List<Long> userIds,
                                    List<UserGatewayDTO> removedLocations,
                                    LocalDateTime occurredOn) implements BrokerEvent {

    public ConnectionSyncedEvent(String gatewayId,
                                 List<Long> userIds,
                                 List<UserGatewayDTO> removedLocations) {
        this(gatewayId, userIds, removedLocations, LocalDateTime.now());
    }

    public ConnectionSyncedEvent {
        userIds = userIds == null ? List.of() : List.copyOf(userIds);
        removedLocations = removedLocations == null ? List.of() : List.copyOf(removedLocations);
    }
}
