package com.co.kc.imchat.broker.adapter.cluster.consensus;

import com.co.kc.imchat.broker.adapter.cluster.LocalBroker;
import com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerCluster;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 提交权威集群 change，并提供共识提交所需的 Leader 与只读投影能力。
 */
public interface BrokerClusterConsensus {

    BrokerCluster getCluster();

    BrokerClusterState committedState();

    Optional<BrokerClusterOperation> findOperation(ClusterOperationId operationId);

    /** 查询目标 epoch 唯一的未完成 drain operation。 */
    Optional<BrokerClusterOperation> findActiveDrainOperation(RoutingEpoch targetEpoch);

    /** 查询目标 epoch 唯一的未完成优雅 drain 或故障路由重建 operation。 */
    Optional<BrokerClusterOperation> findActiveRouteRecoveryOperation(RoutingEpoch targetEpoch);

    /** 将一条 Broker 共识命令写入 Raft 日志，返回多数派提交后本机应用的结果。 */
    CompletionStage<BrokerClusterConsensusResult> submitChange(BrokerClusterConsensusCommand command);

    /** 本机 Broker 的角色、已知 Leader 和本机应用进度入口，不是领域实体。 */
    LocalBroker getLocalBroker();
}
