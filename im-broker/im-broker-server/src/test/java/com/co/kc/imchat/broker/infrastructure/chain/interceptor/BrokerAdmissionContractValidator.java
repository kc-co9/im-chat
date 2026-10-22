package com.co.kc.imchat.broker.infrastructure.chain.interceptor;

import com.co.kc.imchat.broker.interfaces.handler.AbstractBrokerRpcHandler;
import com.co.kc.imchat.broker.sdk.access.BrokerDataPlaneRequest;
import com.co.kc.imchat.broker.sdk.access.BrokerPeerRequest;
import com.co.kc.imchat.broker.sdk.access.GatewayRouteRequest;
import com.co.kc.imchat.broker.sdk.access.ServiceDataPlaneRequest;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.BrokerHealthReportParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerProbeParams;
import com.co.kc.imchat.broker.sdk.model.params.QuorumReadIndexParams;
import com.co.kc.imchat.broker.sdk.model.params.RouteLeaseInstallParams;
import com.co.kc.imchat.broker.sdk.model.params.RouteLeasePrepareParams;
import com.co.kc.imchat.broker.sdk.model.params.RoutingFenceParams;
import com.co.kc.imchat.plugin.gossip.model.param.GossipPeerRequest;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/** 仅供测试验证实际 Handler 装配，不参与生产启动。 */
public final class BrokerAdmissionContractValidator {
    private BrokerAdmissionContractValidator() {
    }

    public static void validateHandlers(Collection<? extends AbstractBrokerRpcHandler<?, ?>> handlers) {
        Set<BrokerBoltOperation> operations = EnumSet.noneOf(BrokerBoltOperation.class);
        for (AbstractBrokerRpcHandler<?, ?> handler : handlers) {
            if (!operations.add(handler.brokerOperation())) {
                throw new IllegalStateException("duplicate Broker operation");
            }
            validateRequestType(handler.brokerOperation(), handler.requestType());
        }
        if (!operations.equals(EnumSet.allOf(BrokerBoltOperation.class))) {
            throw new IllegalStateException("missing Broker operation");
        }
    }

    static void validateRequestType(BrokerBoltOperation operation, Class<?> requestType) {
        boolean matches = switch (operation.admissionType()) {
            case CLUSTER_READ -> true;
            case BROKER_OBSERVATION -> switch (operation) {
                case PROBE_BROKER -> requestType == BrokerProbeParams.class;
                case FETCH_BROKER_HEALTH_REPORT -> requestType == BrokerHealthReportParams.class;
                default -> false;
            };
            case BROKER_PEER -> BrokerPeerRequest.class.isAssignableFrom(requestType)
                    || GossipPeerRequest.class.isAssignableFrom(requestType);
            case BROKER_CONTROL -> switch (operation) {
                case FETCH_QUORUM_READ_INDEX -> requestType == QuorumReadIndexParams.class;
                case PREPARE_ROUTE_LEASE -> requestType == RouteLeasePrepareParams.class;
                case INSTALL_ROUTE_LEASE -> requestType == RouteLeaseInstallParams.class;
                case FENCE_ROUTING_EPOCH -> requestType == RoutingFenceParams.class;
                default -> false;
            };
            case GATEWAY_BOOTSTRAP, GATEWAY_ROUTE -> GatewayRouteRequest.class.isAssignableFrom(requestType);
            case BROKER_DATA_PLANE -> BrokerDataPlaneRequest.class.isAssignableFrom(requestType);
            case SERVICE_DATA_PLANE -> ServiceDataPlaneRequest.class.isAssignableFrom(requestType);
        };
        if (!matches) {
            throw new IllegalStateException("Broker request capability does not match admission type: "
                    + operation + " -> " + requestType.getName());
        }
    }
}
