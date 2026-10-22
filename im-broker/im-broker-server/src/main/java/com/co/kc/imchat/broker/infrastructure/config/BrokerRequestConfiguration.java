package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.domain.authority.service.LocalRoutingLeaseService;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestInterceptorChain;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerCallerIdentityInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerHandlerInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerPeerAdmissionInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerRequestInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.BrokerRouteCallerInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.GatewayRegistrationInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.GatewayRouteAuthorizationInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.RouteBrokerExecutionInterceptor;
import com.co.kc.imchat.broker.infrastructure.chain.interceptor.ServiceCallerRoleInterceptor;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.RegistryProperties;
import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.access.BrokerAdmissionType;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.plugin.bolt.core.BoltRequestProcessor;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按 operation 组装 Broker Bolt 入口：前置准入节点依次执行，业务 Handler 位于链尾。
 */
@Configuration
public class BrokerRequestConfiguration {

    @Bean
    public BoltRequestProcessor brokerBoltRequestProcessor(
            BrokerRaftProperties raftProperties,
            BrokerRoutingProperties routingProperties,
            RegistryProperties registryProperties,
            BrokerRaftAdapter stateRepository,
            BrokerRaftAdapter raftRepository,
            LocalRoutingLeaseService leaseManager,
            GatewayRegistry gatewayRegistry,
            List<AbstractBrokerRpcHandler<?, ?>> handlers) {
        EnumSet<BrokerAdmissionType> trustedNetworkProfiles =
                EnumSet.noneOf(BrokerAdmissionType.class);
        if (routingProperties.isAllowTrustedNetworkGatewayIdentity()) {
            trustedNetworkProfiles.add(BrokerAdmissionType.GATEWAY_BOOTSTRAP);
            trustedNetworkProfiles.add(BrokerAdmissionType.GATEWAY_ROUTE);
        }
        if (routingProperties.isAllowTrustedNetworkBrokerIdentity()) {
            trustedNetworkProfiles.add(BrokerAdmissionType.BROKER_PEER);
            trustedNetworkProfiles.add(BrokerAdmissionType.BROKER_CONTROL);
            trustedNetworkProfiles.add(BrokerAdmissionType.BROKER_DATA_PLANE);
        }
        if (routingProperties.isAllowTrustedNetworkServiceIdentity()) {
            trustedNetworkProfiles.add(BrokerAdmissionType.CLUSTER_READ);
            trustedNetworkProfiles.add(BrokerAdmissionType.SERVICE_DATA_PLANE);
        }
        BrokerCallerIdentityInterceptor workload = new BrokerCallerIdentityInterceptor(
                Map.of(
                        BrokerCallerRole.GATEWAY, routingProperties.getGatewayWorkloadIdentityPrefix(),
                        BrokerCallerRole.BROKER, routingProperties.getBrokerWorkloadIdentityPrefix(),
                        BrokerCallerRole.MESSAGE, routingProperties.getMessageWorkloadIdentityPrefix(),
                        BrokerCallerRole.ACCOUNT, routingProperties.getAccountWorkloadIdentityPrefix(),
                        BrokerCallerRole.OPS, routingProperties.getOpsWorkloadIdentityPrefix()),
                trustedNetworkProfiles);
        List<BrokerRequestInterceptor> admission = List.of(
                // 1. 认证调用方，再校验 Gateway/Broker 身份绑定和成员资格。
                workload,
                new GatewayRegistrationInterceptor(),
                new GatewayRouteAuthorizationInterceptor(gatewayRegistry,
                        registryProperties.getGatewayTtl()),
                new BrokerPeerAdmissionInterceptor(raftProperties.getNodeId(), stateRepository,
                        routingProperties.getBrokerWorkloadIdentityPrefix(), raftProperties.getClusterId()),
                new BrokerRouteCallerInterceptor(stateRepository,
                        routingProperties.getBrokerWorkloadIdentityPrefix(),
                        routingProperties.isAllowTrustedNetworkBrokerIdentity()),
                // 2. 核对业务调用方角色，随后用 Lease 环绕真实业务执行。
                new ServiceCallerRoleInterceptor(),
                new RouteBrokerExecutionInterceptor(raftProperties.getNodeId(),
                        stateRepository, leaseManager, raftRepository::getLeaderEndpoint));

        List<BoltRequestHandler> entries = new ArrayList<>();
        for (AbstractBrokerRpcHandler<?, ?> handler : handlers) {
            List<BrokerRequestInterceptor> nodes = new ArrayList<>(admission);
            // 3. 业务处理位于链尾，列表顺序就是每次请求的执行顺序。
            nodes.add(new BrokerHandlerInterceptor(handler));
            entries.add(new BrokerRequestInterceptorChain(nodes));
        }
        return new BoltRequestProcessor(entries);
    }
}
