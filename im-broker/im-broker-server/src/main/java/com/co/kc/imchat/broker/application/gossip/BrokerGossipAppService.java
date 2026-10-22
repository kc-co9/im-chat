package com.co.kc.imchat.broker.application.gossip;

import com.co.kc.imchat.broker.domain.connection.model.GatewayId;

import com.co.kc.imchat.broker.domain.cluster.repository.BrokerRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.adapter.gossip.BrokerGossipAdapter;
import com.co.kc.imchat.broker.model.cqrs.event.BrokerHeartbeatEvent;
import com.co.kc.imchat.broker.model.cqrs.event.BrokerRegisteredEvent;
import com.co.kc.imchat.broker.model.cqrs.event.BrokerRemovedEvent;
import com.co.kc.imchat.broker.model.cqrs.event.ConnectionRegisteredEvent;
import com.co.kc.imchat.broker.model.cqrs.event.ConnectionRemovedEvent;
import com.co.kc.imchat.broker.model.cqrs.event.ConnectionSyncedEvent;
import com.co.kc.imchat.broker.model.cqrs.event.GatewayHeartbeatEvent;
import com.co.kc.imchat.broker.model.cqrs.event.GatewayRegisteredEvent;
import com.co.kc.imchat.broker.model.cqrs.event.GatewayRemovedEvent;

/**
 * 将 Broker 模块的 Broker、Gateway 和 Connection 注册事件更新为 Gossip observation；不修改 Raft 权威拓扑，不负责 RPC 或调度。
 */
public final class BrokerGossipAppService {
    private final BrokerRegistry brokerRegistry;
    private final GatewayRegistry gatewayRegistry;
    private final BrokerGossipAdapter brokerGossipAdapter;

    public BrokerGossipAppService(BrokerRegistry brokerRegistry,
                                  GatewayRegistry gatewayRegistry,
                                  BrokerGossipAdapter brokerGossipAdapter) {
        this.brokerRegistry = brokerRegistry;
        this.gatewayRegistry = gatewayRegistry;
        this.brokerGossipAdapter = brokerGossipAdapter;
    }

    /**
     * 发布本机新注册 Broker 的运行观察。
     */
    public void observeBrokerRegistration(BrokerRegisteredEvent event) {
        brokerGossipAdapter.putBrokerState(event.brokerId(), event.host(), event.port());
    }

    /**
     * 心跳以注册表当前端点为准，已移除节点不被迟到心跳复活。
     */
    public void observeBrokerHeartbeat(BrokerHeartbeatEvent event) {
        brokerRegistry.find(event.brokerId()).ifPresent(endpoint -> brokerGossipAdapter.putBrokerState(
                endpoint.brokerId(), endpoint.host(), endpoint.port()));
    }

    /**
     * 发布 Broker 移除的 tombstone。
     */
    public void observeBrokerRemoval(BrokerRemovedEvent event) {
        brokerGossipAdapter.removeBrokerState(event.brokerId());
    }

    /**
     * 发布新注册 Gateway 的端点与工作负载身份。
     */
    public void observeGatewayRegistration(GatewayRegisteredEvent event) {
        brokerGossipAdapter.putGatewayState(event.gatewayId(), event.host(), event.port(), event.workloadIdentity());
    }

    /**
     * 心跳刷新注册表当前 Gateway 的观察，保留其受信身份。
     */
    public void observeGatewayHeartbeat(GatewayHeartbeatEvent event) {
        gatewayRegistry.find(new GatewayId(event.gatewayId())).ifPresent(endpoint -> brokerGossipAdapter.putGatewayState(
                endpoint.gatewayId().value(), endpoint.host().value(), endpoint.port().value(), endpoint.principalId().value()));
    }

    /**
     * 发布 Gateway 移除事实，并由状态存储清理该 Gateway 的连接观察。
     */
    public void observeGatewayRemoval(GatewayRemovedEvent event) {
        brokerGossipAdapter.removeGatewayState(event.gatewayId());
    }

    /**
     * 发布用户到 Gateway 的连接观察。
     */
    public void observeConnectionRegistration(ConnectionRegisteredEvent event) {
        brokerGossipAdapter.putConnectionState(event.userId(), event.gatewayId());
    }

    /**
     * 发布指定用户与 Gateway 连接位置的 tombstone。
     */
    public void observeConnectionRemoval(ConnectionRemovedEvent event) {
        brokerGossipAdapter.removeConnectionState(event.userId(), event.gatewayId());
    }

    /**
     * 根据同步事件更新 Gateway 完整连接观察及明确移除的位置。
     */
    public void observeConnectionSnapshot(ConnectionSyncedEvent event) {
        brokerGossipAdapter.updateConnectionSnapshot(event.gatewayId(), event.userIds(), event.removedLocations());
    }
}
