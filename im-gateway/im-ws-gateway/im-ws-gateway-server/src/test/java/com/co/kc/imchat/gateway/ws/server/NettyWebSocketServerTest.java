package com.co.kc.imchat.gateway.ws.server;

import com.co.kc.imchat.broker.sdk.model.params.ConnectionRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import com.co.kc.imchat.gateway.ws.support.BrokerClientTestSupport;
import com.co.kc.imchat.common.model.enums.ServiceName;
import com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class NettyWebSocketServerTest {

    @Test
    void stopUnregistersGatewayFromBroker() {
        CapturingConnectionCleanupClient brokerClient = new CapturingConnectionCleanupClient();
        ConnectionRegistry connectionRegistry = new ConnectionRegistry();
        connectionRegistry.register(1L, "session-1", "conn-1", new EmbeddedChannel());
        NettyWebSocketServer server = new NettyWebSocketServer(
                0, "gw-1", "/ws", brokerClient, connectionRegistry, null, 60, 65536);

        server.stop();

        assertThat(brokerClient.unregisterGatewayCommand)
                .isEqualTo(new GatewayUnregisterParams("gw-1"));
        assertThat(connectionRegistry.activeConnectionIds()).isEmpty();
    }

    @Test
    void startReleasesEventLoopGroupsWhenBindFails() throws Exception {
        NettyWebSocketServer server = new NettyWebSocketServer(
                -1, "gw-1", "/ws", new CapturingConnectionCleanupClient(),
                new ConnectionRegistry(), null, 60, 65536);

        assertThatThrownBy(server::start).isInstanceOf(IllegalArgumentException.class);

        assertThat(eventLoopGroup(server, "bossGroup").isShuttingDown()).isTrue();
        assertThat(eventLoopGroup(server, "workerGroup").isShuttingDown()).isTrue();
    }

    private EventLoopGroup eventLoopGroup(NettyWebSocketServer server, String fieldName) throws Exception {
        Field field = NettyWebSocketServer.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (EventLoopGroup) field.get(server);
    }

    private static class CapturingConnectionCleanupClient extends BrokerClient {
        private GatewayUnregisterParams unregisterGatewayCommand;

        private CapturingConnectionCleanupClient() {
            super(BrokerClientTestSupport.invoker(), BrokerClientTestSupport.discovery(),
                    ServiceName.IM_BROKER, BrokerLoadBalance.HASH, 3000);
        }

        @Override
        public void registerGateway(GatewayRegisterParams command) {
        }

        @Override
        public void unregisterGateway(GatewayUnregisterParams command) {
            this.unregisterGatewayCommand = command;
        }

        @Override
        public void registerConnection(ConnectionRegisterParams command) {
        }

        @Override
        public void unregisterConnection(ConnectionUnregisterParams command) {
        }

        @Override
        public BrokerFrameWriteResult writeFrame(BrokerFrameWriteParams command) {
            return BrokerFrameWriteResult.ok();
        }
    }
}
