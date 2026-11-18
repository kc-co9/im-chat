package com.co.kc.imchat.management.ops.transformer.interfaces;

import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerNodeDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerNodeFailureDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerNodeOverviewDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.ClusterOverviewDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.ClusterQueryResultDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.ConnectionRouteDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.GatewayNodeDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.GossipRecordDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.SourcedValueDTO;
import com.co.kc.imchat.management.ops.model.io.BrokerFailureResponse;
import com.co.kc.imchat.management.ops.model.io.BrokerNodeResponse;
import com.co.kc.imchat.management.ops.model.io.BrokerOverviewResponse;
import com.co.kc.imchat.management.ops.model.io.ClusterQueryResponse;
import com.co.kc.imchat.management.ops.model.io.ConnectionRouteResponse;
import com.co.kc.imchat.management.ops.model.io.GatewayNodeResponse;
import com.co.kc.imchat.management.ops.model.io.GossipRecordResponse;
import com.co.kc.imchat.management.ops.model.io.OpsOverviewResponse;
import com.co.kc.imchat.management.ops.model.io.SourcedResponse;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerInstanceDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.DiagnosticSummaryDTO;
import com.co.kc.imchat.management.ops.model.io.BrokerInstanceResponse;
import com.co.kc.imchat.management.ops.model.io.DiagnosticSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/** Ops HTTP 边界转换器。 */
@Mapper
public interface OpsHttpTransformer {
    OpsHttpTransformer INSTANCE = Mappers.getMapper(OpsHttpTransformer.class);

    OpsOverviewResponse opsOverviewResponseFrom(ClusterOverviewDTO overview);

    BrokerOverviewResponse brokerOverviewResponseFrom(BrokerNodeOverviewDTO overview);

    @Mapping(target = "registeredAtMs", source = "registeredAt")
    @Mapping(target = "lastSeenAtMs", source = "lastSeenAt")
    BrokerNodeResponse brokerNodeResponseFrom(BrokerNodeDTO node);

    @Mapping(target = "registeredAtMs", source = "registeredAt")
    @Mapping(target = "lastSeenAtMs", source = "lastSeenAt")
    GatewayNodeResponse gatewayNodeResponseFrom(GatewayNodeDTO node);

    @Mapping(target = "registeredAtMs", source = "registeredAt")
    @Mapping(target = "lastSeenAtMs", source = "lastSeenAt")
    ConnectionRouteResponse connectionRouteResponseFrom(ConnectionRouteDTO route);

    @Mapping(target = "executedAtMs", source = "executedAt")
    @Mapping(target = "durationMs", source = "duration")
    GossipRecordResponse gossipRecordResponseFrom(GossipRecordDTO record);

    BrokerFailureResponse brokerFailureResponseFrom(BrokerNodeFailureDTO failure);

    default ClusterQueryResponse<BrokerNodeResponse> brokerQueryResponseFrom(
            ClusterQueryResultDTO<BrokerNodeDTO> result) {
        return queryResponseFrom(result, this::brokerNodeResponseFrom);
    }

    default ClusterQueryResponse<GatewayNodeResponse> gatewayQueryResponseFrom(
            ClusterQueryResultDTO<GatewayNodeDTO> result) {
        return queryResponseFrom(result, this::gatewayNodeResponseFrom);
    }

    default ClusterQueryResponse<ConnectionRouteResponse> connectionQueryResponseFrom(
            ClusterQueryResultDTO<ConnectionRouteDTO> result) {
        return queryResponseFrom(result, this::connectionRouteResponseFrom);
    }

    default ClusterQueryResponse<GossipRecordResponse> gossipQueryResponseFrom(
            ClusterQueryResultDTO<GossipRecordDTO> result) {
        return queryResponseFrom(result, this::gossipRecordResponseFrom);
    }

    default Long epochMilliFrom(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    private <S, T> ClusterQueryResponse<T> queryResponseFrom(
            ClusterQueryResultDTO<S> result,
            Function<S, T> mapper
    ) {
        List<SourcedResponse<T>> values = result.values().stream()
                .map(value -> sourcedResponseFrom(value, mapper))
                .toList();
        List<BrokerFailureResponse> failures = result.failures().stream()
                .map(this::brokerFailureResponseFrom)
                .toList();
        return new ClusterQueryResponse<>(values, failures, epochMilliFrom(result.queriedAt()));
    }

    private <S, T> SourcedResponse<T> sourcedResponseFrom(
            SourcedValueDTO<S> value,
            Function<S, T> mapper
    ) {
        return new SourcedResponse<>(value.sourceBroker(), mapper.apply(value.value()));
    }

    @Mapping(target = "startedAtMs", source = "startedAt")
    @Mapping(target = "uptimeMs", source = "uptime")
    BrokerInstanceResponse brokerInstanceFrom(BrokerInstanceDTO source);

    @Mapping(target = "lastSuccessAtMs", source = "lastSuccessAt")
    @Mapping(target = "lastFailureAtMs", source = "lastFailureAt")
    @Mapping(target = "lastExecutedAtMs", source = "lastExecutedAt")
    DiagnosticSummaryResponse diagnosticSummaryFrom(DiagnosticSummaryDTO source);

    default Long durationMsFrom(Duration value) {
        return value == null ? null : value.toMillis();
    }

}
