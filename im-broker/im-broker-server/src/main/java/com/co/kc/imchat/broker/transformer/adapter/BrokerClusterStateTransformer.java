package com.co.kc.imchat.broker.transformer.adapter;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerMembershipRole;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerNode;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerRouteEndpoint;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerSelectionAlgorithm;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerTrafficState;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterRevision;
import com.co.kc.imchat.broker.domain.cluster.model.ConsensusMembership;
import com.co.kc.imchat.broker.domain.cluster.model.OperationHistoryCapacity;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpochFence;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.plugin.raft.model.RaftMembership;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ValueMapping;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 在 Server 领域聚合与 SDK 只读投影之间转换。
 */
@Mapper
public interface BrokerClusterStateTransformer {

    BrokerClusterStateTransformer INSTANCE =
            Mappers.getMapper(BrokerClusterStateTransformer.class);

    default BrokerCluster brokerClusterFrom(
            BrokerClusterState state,
            List<BrokerClusterOperation> operations,
            RoutingEpochFence fence,
            int operationHistoryCapacity) {
        return brokerClusterFrom(state, operations, fence, operationHistoryCapacity, Map.of());
    }

    default BrokerCluster brokerClusterFrom(
            BrokerClusterState state,
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
                .brokers(brokerNodesFrom(state.brokers()))
                .operations(operations)
                .membershipEndpoints(membershipEndpoints.entrySet().stream().collect(Collectors.toMap(
                        entry -> new BrokerId(entry.getKey()),
                        entry -> membershipEndpointFrom(entry.getValue()))))
                .routingFence(fence)
                .operationHistoryCapacity(new OperationHistoryCapacity(operationHistoryCapacity))
                .build();
    }

    default BrokerClusterState brokerClusterStateFrom(BrokerCluster cluster) {
        if (cluster == null) {
            throw new IllegalArgumentException("broker cluster is required");
        }
        return brokerClusterStateFrom(new ClusterProjection(
                cluster.revision().value(), cluster.epoch().value(), cluster.algorithm(),
                cluster.brokers()));
    }

    BrokerClusterState brokerClusterStateFrom(ClusterProjection projection);

    List<BrokerNode> brokerNodesFrom(List<com.co.kc.imchat.broker.sdk.cluster.BrokerNode> nodes);

    @Mapping(target = "id", source = "brokerId")
    @Mapping(target = "trafficState", source = "routingState")
    BrokerNode brokerNodeFrom(
            com.co.kc.imchat.broker.sdk.cluster.BrokerNode node);

    @Mapping(target = "brokerId", source = "id.value")
    @Mapping(target = "routeEndpoint", source = "routeEndpoint.value")
    @Mapping(target = "routingState", source = "trafficState")
    com.co.kc.imchat.broker.sdk.cluster.BrokerNode brokerSdkNodeFrom(NodeProjection node);

    default com.co.kc.imchat.broker.sdk.cluster.BrokerNode brokerSdkNodeFrom(BrokerNode node) {
        return node == null ? null : brokerSdkNodeFrom(new NodeProjection(node.id(), node.routeEndpoint(),
                node.membershipRole(), node.trafficState()));
    }

    @ValueMapping(source = "REMOVED", target = MappingConstants.THROW_EXCEPTION)
    BrokerMembershipRole brokerMembershipRoleFrom(
            com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole role);

    ConsensusMembership consensusMembershipFrom(RaftMembership membership);

    Set<MembershipEndpoint> membershipEndpointsFrom(List<String> endpoints);

    @Mapping(target = "value", source = "endpoint")
    MembershipEndpoint membershipEndpointFrom(String endpoint);

    default BrokerId brokerIdFrom(String value) {
        return new BrokerId(value);
    }

    default BrokerRouteEndpoint brokerRouteEndpointFrom(String value) {
        return new BrokerRouteEndpoint(value);
    }

    record ClusterProjection(
            /* 集群状态版本。 */
            long revision,
            /* 路由纪元。 */
            long epoch,
            /* Broker 选择算法。 */
            BrokerSelectionAlgorithm algorithm,
            /* 集群节点。 */
            List<BrokerNode> brokers) {
    }

    record NodeProjection(
            /* 节点身份。 */
            BrokerId id,
            /* 节点路由端点。 */
            BrokerRouteEndpoint routeEndpoint,
            /* 共识成员角色。 */
            BrokerMembershipRole membershipRole,
            /* 流量状态。 */
            BrokerTrafficState trafficState) {
    }
}
