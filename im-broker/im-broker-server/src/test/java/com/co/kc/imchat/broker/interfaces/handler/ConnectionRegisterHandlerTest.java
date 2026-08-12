package com.co.kc.imchat.broker.interfaces.handler;

import com.co.kc.imchat.broker.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.domain.registry.broker.memory.InMemoryBrokerRegistry;
import com.co.kc.imchat.broker.domain.registry.connection.memory.InMemoryConnectionRegistry;
import com.co.kc.imchat.broker.domain.service.BrokerConnectionService;
import com.co.kc.imchat.broker.interfaces.handler.connection.ConnectionRegisterHandler;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionRegisterParams;
import com.co.kc.imchat.broker.support.client.BrokerPeerClient;
import com.co.kc.imchat.broker.support.event.publisher.NoopBrokerEventPublisher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConnectionRegisterHandlerTest {
    private static final String BROKER_1_HOST = "10.0.0.1";
    private static final String BROKER_2_HOST = "10.0.0.2";
    private static final int BROKER_PORT = 12200;
    private static final String BROKER_1_ID = brokerId(BROKER_1_HOST, BROKER_PORT);
    private static final String BROKER_2_ID = brokerId(BROKER_2_HOST, BROKER_PORT);

    @Test
    void forwardsConnectionRegisterToOwnerBroker() throws Exception {
        InMemoryConnectionRegistry connectionRegistry = new InMemoryConnectionRegistry();
        InMemoryBrokerRegistry brokerRegistry = new InMemoryBrokerRegistry();
        BrokerEndpointDTO owner = ownerBrokerForUser(brokerRegistry, 1L);
        BrokerPeerClient peerClient = mock(BrokerPeerClient.class);
        BrokerConnectionService brokerConnectionService = new BrokerConnectionService(connectionRegistry, brokerRegistry,
                brokerProperties("127.0.0.1", BROKER_PORT), peerClient, new NoopBrokerEventPublisher());
        ConnectionRegisterHandler handler = new ConnectionRegisterHandler(connectionRegistry, new NoopBrokerEventPublisher(),
                brokerConnectionService, peerClient);
        ConnectionRegisterParams params = new ConnectionRegisterParams(1L, "gw-1");

        handler.handle(com.co.kc.imchat.common.utils.JsonUtils.toJson(params));

        verify(peerClient).registerConnection(owner, params);
        assertThat(connectionRegistry.find(1L)).isEmpty();
    }

    private BrokerEndpointDTO ownerBrokerForUser(InMemoryBrokerRegistry brokerRegistry, Long userId) {
        brokerRegistry.register(BROKER_1_ID, BROKER_1_HOST, BROKER_PORT);
        brokerRegistry.register(BROKER_2_ID, BROKER_2_HOST, BROKER_PORT);
        return brokerRegistry.list().stream()
                .filter(broker -> broker.brokerId().equals(expectedBrokerId(userId)))
                .findFirst()
                .orElseThrow();
    }

    private String expectedBrokerId(Long userId) {
        return Math.floorMod(String.valueOf(userId).hashCode(), 2) == 0 ? BROKER_1_ID : BROKER_2_ID;
    }

    private BrokerProperties brokerProperties(String host, int port) {
        BrokerProperties properties = new BrokerProperties();
        properties.getInstance().setHost(host);
        properties.getInstance().setPort(port);
        return properties;
    }

    private static String brokerId(String host, int port) {
        return "broker-" + host + "-" + port;
    }
}
