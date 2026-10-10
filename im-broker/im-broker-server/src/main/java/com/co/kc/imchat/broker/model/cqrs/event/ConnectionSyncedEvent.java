package com.co.kc.imchat.broker.model.cqrs.event;

import com.co.kc.imchat.broker.domain.connection.model.ConnectionRegistration;
import com.co.kc.imchat.common.domain.shared.event.DomainEvent;

import java.time.LocalDateTime;
import java.util.List;

public record ConnectionSyncedEvent(String gatewayId,
                                    List<Long> userIds,
                                    List<ConnectionRegistration> removedLocations,
                                    LocalDateTime occurredOn) implements DomainEvent {

    public ConnectionSyncedEvent(String gatewayId,
                                 List<Long> userIds,
                                 List<ConnectionRegistration> removedLocations) {
        this(gatewayId, userIds, removedLocations, LocalDateTime.now());
    }

    public ConnectionSyncedEvent {
        userIds = userIds == null ? List.of() : List.copyOf(userIds);
        removedLocations = removedLocations == null ? List.of() : List.copyOf(removedLocations);
    }
}
