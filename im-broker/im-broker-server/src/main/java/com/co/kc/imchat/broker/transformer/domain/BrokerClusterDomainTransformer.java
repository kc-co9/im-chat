package com.co.kc.imchat.broker.transformer.domain;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerRouteEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.OperationHistoryCapacity;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ValueMapping;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 在 Server 领域聚合与 SDK 只读投影之间转换。 */
@Mapper
public interface BrokerClusterDomainTransformer {

    BrokerClusterDomainTransformer INSTANCE =
            Mappers.getMapper(BrokerClusterDomainTransformer.class);

    default BrokerCluster restoreAggregate(BrokerClusterState state,
                                           List<BrokerClusterOperation> operations,
                                           RoutingEpochFence fence,
                                           int operationHistoryCapacity) {
        return restoreAggregate(state, operations, fence, operationHistoryCapacity, Map.of());
    }

    default BrokerCluster restoreAggregate(BrokerClusterState state,
                                           List<BrokerClusterOperation> operations,
                                           RoutingEpochFence fence,
                                           int operationHistoryCapacity,
                                           Map<String, String> membershipEndpoints) {
        if (state == null) {
            throw new IllegalArgumentException("broker cluster state is required");
        }
        return BrokerCluster.builder()
                .revision(new ClusterRevision(state.revision()))
                .epoch(new RoutingEpoch(state.epoch()))
                .algorithm(BrokerSelectionAlgorithm.valueOf(state.algorithm().name()))
                .brokers(toDomainNodes(state.brokers()))
                .operations(operations)
                .membershipEndpoints(membershipEndpoints.entrySet().stream().collect(Collectors.toMap(
                        entry -> new BrokerId(entry.getKey()),
                        entry -> new MembershipEndpoint(entry.getValue()))))
                .routingFence(fence)
                .operationHistoryCapacity(new OperationHistoryCapacity(operationHistoryCapacity))
                .build();
    }

    default BrokerClusterState toSdkState(BrokerCluster cluster) {
        if (cluster == null) {
            throw new IllegalArgumentException("broker cluster is required");
        }
        return toSdkState(new ClusterProjection(
                cluster.revision().value(), cluster.epoch().value(), cluster.algorithm(),
                cluster.brokers()));
    }

    BrokerClusterState toSdkState(ClusterProjection projection);

    List<com.co.kc.imchat.broker.domain.cluster.model.BrokerNode> toDomainNodes(
            List<com.co.kc.imchat.broker.sdk.cluster.BrokerNode> nodes);

    @Mapping(target = "id", source = "brokerId")
    @Mapping(target = "trafficState", source = "routingState")
    com.co.kc.imchat.broker.domain.cluster.model.BrokerNode toDomainNode(
            com.co.kc.imchat.broker.sdk.cluster.BrokerNode node);

    @Mapping(target = "brokerId", source = "id.value")
    @Mapping(target = "routeEndpoint", source = "routeEndpoint.value")
    @Mapping(target = "routingState", source = "trafficState")
    com.co.kc.imchat.broker.sdk.cluster.BrokerNode toSdkNode(
            NodeProjection node);

    default com.co.kc.imchat.broker.sdk.cluster.BrokerNode toSdkNode(
            com.co.kc.imchat.broker.domain.cluster.model.BrokerNode node) {
        return node == null ? null : toSdkNode(new NodeProjection(node.id(), node.routeEndpoint(),
                node.membershipRole(), node.trafficState()));
    }

    @ValueMapping(source = "REMOVED", target = MappingConstants.THROW_EXCEPTION)
    com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipRole toDomainMembershipRole(
            com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole role);

    default BrokerId toBrokerId(String value) {
        return new BrokerId(value);
    }

    default BrokerRouteEndpoint toBrokerRouteEndpoint(String value) {
        return new BrokerRouteEndpoint(value);
    }

    record ClusterProjection(
            long revision,
            long epoch,
            com.co.kc.imchat.broker.domain.cluster.model.BrokerSelectionAlgorithm algorithm,
            List<com.co.kc.imchat.broker.domain.cluster.model.BrokerNode> brokers) {
    }

    record NodeProjection(
            BrokerId id,
            BrokerRouteEndpoint routeEndpoint,
            com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipRole membershipRole,
            com.co.kc.imchat.broker.domain.cluster.model.BrokerTrafficState trafficState) {
    }
}
