package com.co.kc.imchat.management.monitor.transformer.interfaces;

import com.co.kc.imchat.management.monitor.model.cqrs.dto.BrokerNodeDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.BrokerNodeFailureDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.BrokerNodeOverviewDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.ClusterOverviewDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.ClusterQueryResultDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.ConnectionRouteDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.GatewayNodeDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.GossipRecordDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.MigrationRecordDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.SourcedValueDTO;
import com.co.kc.imchat.management.monitor.model.io.BrokerFailureResponse;
import com.co.kc.imchat.management.monitor.model.io.BrokerNodeResponse;
import com.co.kc.imchat.management.monitor.model.io.BrokerOverviewResponse;
import com.co.kc.imchat.management.monitor.model.io.ClusterQueryResponse;
import com.co.kc.imchat.management.monitor.model.io.ConnectionRouteResponse;
import com.co.kc.imchat.management.monitor.model.io.GatewayNodeResponse;
import com.co.kc.imchat.management.monitor.model.io.GossipRecordResponse;
import com.co.kc.imchat.management.monitor.model.io.MigrationRecordResponse;
import com.co.kc.imchat.management.monitor.model.io.MonitorOverviewResponse;
import com.co.kc.imchat.management.monitor.model.io.SourcedResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/** Monitor HTTP 边界转换器。 */
@Mapper
public interface MonitorHttpTransformer {
    MonitorHttpTransformer INSTANCE = Mappers.getMapper(MonitorHttpTransformer.class);

    MonitorOverviewResponse monitorOverviewResponseFrom(ClusterOverviewDTO overview);

    BrokerOverviewResponse brokerOverviewResponseFrom(BrokerNodeOverviewDTO overview);

    BrokerNodeResponse brokerNodeResponseFrom(BrokerNodeDTO node);

    GatewayNodeResponse gatewayNodeResponseFrom(GatewayNodeDTO node);

    ConnectionRouteResponse connectionRouteResponseFrom(ConnectionRouteDTO route);

    GossipRecordResponse gossipRecordResponseFrom(GossipRecordDTO record);

    MigrationRecordResponse migrationRecordResponseFrom(MigrationRecordDTO record);

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

    default ClusterQueryResponse<MigrationRecordResponse> migrationQueryResponseFrom(
            ClusterQueryResultDTO<MigrationRecordDTO> result) {
        return queryResponseFrom(result, this::migrationRecordResponseFrom);
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
}
