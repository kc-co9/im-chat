package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.domain.authority.service.LocalRoutingLeaseService;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRaftProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerRoutingProperties;
import com.co.kc.imchat.broker.infrastructure.config.properties.RegistryProperties;
import com.co.kc.imchat.broker.interfaces.handler.broker.BrokerHealthReportHandler;
import com.co.kc.imchat.broker.interfaces.handler.broker.BrokerProbeHandler;
import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.broker.sdk.model.params.BrokerHealthReportParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerProbeParams;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.core.BoltRequestProcessor;
import com.co.kc.imchat.plugin.bolt.model.BoltRequest;
import com.co.kc.imchat.plugin.bolt.model.BoltResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrokerObservationRequestConfigurationTest {
    @Test
    void plaintextStartupWiresBothOperationsButCannotReachApplicationEvenWithFallbackEnabled() {
        BrokerClusterAppService app = mock(BrokerClusterAppService.class);
        BrokerRoutingProperties routing = new BrokerRoutingProperties();
        routing.setAllowTrustedNetworkBrokerIdentity(true);
        BrokerRaftAdapter adapter = mock(BrokerRaftAdapter.class);
        BrokerRaftProperties raft = new BrokerRaftProperties();
        raft.setNodeId("b");
        raft.setClusterId("cluster");
        BoltRequestProcessor processor = new BrokerRequestConfiguration().brokerBoltRequestProcessor(
                raft, routing, new RegistryProperties(), adapter, adapter,
                mock(LocalRoutingLeaseService.class),
                mock(GatewayRegistry.class), List.of(new BrokerProbeHandler(app), new BrokerHealthReportHandler(app)));
        for (BrokerBoltOperation operation : List.of(BrokerBoltOperation.PROBE_BROKER, BrokerBoltOperation.FETCH_BROKER_HEALTH_REPORT)) {
            Object params = operation == BrokerBoltOperation.PROBE_BROKER
                    ? new BrokerProbeParams("cluster", "a", "b", 1L, 7L, "nonce")
                    : new BrokerHealthReportParams(
                            "cluster", "a", "b", 1L, 7L, "nonce", List.of("a"));
            BoltResponse response = (BoltResponse) processor.handleRequest(null, new BoltRequest(
                    operation.service().service(), operation.operation(), JsonUtils.toJson(params)));
            assertThat(response.getCode()).isEqualTo("BOLT_HANDLER_ERROR");
            assertThat(response.getMessage()).contains("verified TLS URI identity");
        }
        verifyNoInteractions(app);
    }
}
