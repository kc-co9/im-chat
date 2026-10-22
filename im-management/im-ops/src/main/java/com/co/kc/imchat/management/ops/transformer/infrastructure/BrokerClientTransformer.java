package com.co.kc.imchat.management.ops.transformer.infrastructure;

import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerNodePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerOverviewPayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.ConnectionRoutePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.GatewayNodePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.GossipRecordPayload;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerNodeDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerOverviewDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.ConnectionRouteDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.GatewayNodeDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.GossipRecordDTO;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerInstancePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.DiagnosticSummaryPayload;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerInstanceDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.DiagnosticSummaryDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Broker 协议载荷与 Ops 投影转换器。 */
@Mapper
public interface BrokerClientTransformer {
    BrokerClientTransformer INSTANCE = Mappers.getMapper(BrokerClientTransformer.class);

    BrokerOverviewDTO brokerOverviewDataFrom(BrokerOverviewPayload payload);

    List<BrokerNodeDTO> brokerNodeDataFrom(List<BrokerNodePayload> payloads);

    List<GatewayNodeDTO> gatewayNodeDataFrom(List<GatewayNodePayload> payloads);

    List<ConnectionRouteDTO> connectionRouteDataFrom(List<ConnectionRoutePayload> payloads);

    List<GossipRecordDTO> gossipRecordDataFrom(List<GossipRecordPayload> payloads);

    @Mapping(target = "startedAt", source = "startedAtMs")
    @Mapping(target = "uptime", source = "uptimeMs")
    BrokerInstanceDTO brokerInstanceFrom(BrokerInstancePayload source);

    @Mapping(target = "registeredAt", source = "registeredAtMs")
    @Mapping(target = "lastSeenAt", source = "lastSeenAtMs")
    BrokerNodeDTO brokerNodeFrom(BrokerNodePayload source);

    @Mapping(target = "registeredAt", source = "registeredAtMs")
    @Mapping(target = "lastSeenAt", source = "lastSeenAtMs")
    GatewayNodeDTO gatewayNodeFrom(GatewayNodePayload source);

    @Mapping(target = "registeredAt", source = "registeredAtMs")
    @Mapping(target = "lastSeenAt", source = "lastSeenAtMs")
    ConnectionRouteDTO connectionRouteFrom(ConnectionRoutePayload source);

    @Mapping(target = "lastSuccessAt", source = "lastSuccessAtMs")
    @Mapping(target = "lastFailureAt", source = "lastFailureAtMs")
    @Mapping(target = "lastExecutedAt", source = "lastExecutedAtMs")
    DiagnosticSummaryDTO diagnosticSummaryFrom(DiagnosticSummaryPayload source);

    @Mapping(target = "executedAt", source = "executedAtMs")
    @Mapping(target = "duration", source = "durationMs")
    GossipRecordDTO gossipRecordFrom(GossipRecordPayload source);

    default Instant instantFrom(Long value) {
        return value == null ? null : Instant.ofEpochMilli(value);
    }
    default Duration durationFrom(Long value) {
        return value == null ? null : Duration.ofMillis(value);
    }

}
