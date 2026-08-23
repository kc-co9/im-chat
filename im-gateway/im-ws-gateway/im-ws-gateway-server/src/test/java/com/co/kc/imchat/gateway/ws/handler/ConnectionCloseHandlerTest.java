package com.co.kc.imchat.gateway.ws.handler;

import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltOperation;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltService;
import com.co.kc.imchat.gateway.ws.sdk.model.params.ConnectionCloseParams;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionCloseHandlerTest {

    @Test
    void closesOnlyMatchingVersionConnections() {
        ConnectionRegistry registry = new ConnectionRegistry();
        EmbeddedChannel oldConnection = new EmbeddedChannel();
        EmbeddedChannel newConnection = new EmbeddedChannel();
        registry.register(1L, "session-old", "conn-old", oldConnection);
        registry.register(1L, "session-new", "conn-new", newConnection);
        ConnectionCloseHandler handler = new ConnectionCloseHandler(registry);

        handler.handle(JsonUtils.toJson(new ConnectionCloseParams(1L, "session-old")));

        assertThat(handler.service()).isEqualTo(GatewayBoltService.CONNECTION.service());
        assertThat(handler.operation()).isEqualTo(GatewayBoltOperation.CLOSE_CONNECTIONS.operation());
        assertThat(oldConnection.isOpen()).isFalse();
        assertThat(newConnection.isOpen()).isTrue();
    }
}
