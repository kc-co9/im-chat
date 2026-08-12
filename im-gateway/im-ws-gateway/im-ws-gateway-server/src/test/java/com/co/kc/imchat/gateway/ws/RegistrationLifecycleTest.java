package com.co.kc.imchat.gateway.ws;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionSyncParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayHeartbeatParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionUnregisterParams;
import com.co.kc.imchat.gateway.ws.lifecycle.RegistrationLifecycle;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

class RegistrationLifecycleTest {

    @Test
    void startupRegistersGatewayAndSyncsConnectionRoutes() throws Exception {
        CapturingBrokerClient brokerClient = new CapturingBrokerClient();
        RegistrationLifecycle lifecycle = lifecycle(brokerClient, new ConnectionRegistry());

        lifecycle.onReady();

        assertThat(brokerClient.registerGatewayCommand)
                .isEqualTo(new GatewayRegisterParams("gw-1", "127.0.0.1", 12201));
        assertThat(brokerClient.syncConnectionsCommand)
                .isEqualTo(new ConnectionSyncParams("gw-1", List.of()));
    }

    @Test
    void scheduledRefreshHeartbeatsGatewayBeforeSyncingConnectionRoutes() {
        CapturingBrokerClient brokerClient = new CapturingBrokerClient();
        RegistrationLifecycle lifecycle = lifecycle(brokerClient, new ConnectionRegistry());

        lifecycle.onReady();
        brokerClient.events = "";
        lifecycle.refresh();

        assertThat(brokerClient.events).isEqualTo("heartbeat,sync");
        assertThat(brokerClient.heartbeatGatewayCommand)
                .isEqualTo(new GatewayHeartbeatParams("gw-1"));
    }

    @Test
    void scheduledRefreshRegistersGatewayWhenStartupRegistrationFailed() {
        FailOnceBrokerClient brokerClient = new FailOnceBrokerClient();
        RegistrationLifecycle lifecycle = lifecycle(brokerClient, new ConnectionRegistry());

        lifecycle.onReady();
        lifecycle.refresh();

        assertThat(brokerClient.events).isEqualTo("register,register,sync");
    }

    @Test
    void syncsActiveUserIdsToBroker() {
        CapturingBrokerClient brokerClient = new CapturingBrokerClient();
        ConnectionRegistry connectionRegistry = new ConnectionRegistry();
        EmbeddedChannel activeChannel = new EmbeddedChannel();
        EmbeddedChannel inactiveChannel = new EmbeddedChannel();
        inactiveChannel.close();
        connectionRegistry.register(1L, "conn-1", activeChannel);
        connectionRegistry.register(2L, "conn-2", inactiveChannel);
        RegistrationLifecycle lifecycle = lifecycle(brokerClient, connectionRegistry);

        lifecycle.refresh();

        assertThat(brokerClient.syncConnectionsCommand)
                .isEqualTo(new ConnectionSyncParams("gw-1", List.of(1L)));
    }

    @Test
    void startupSkipsFailureWhenBrokerIsTemporarilyUnavailable() {
        FailingBrokerClient brokerClient = new FailingBrokerClient();
        RegistrationLifecycle lifecycle = lifecycle(brokerClient, new ConnectionRegistry());

        assertThatCode(lifecycle::onReady).doesNotThrowAnyException();

        assertThat(brokerClient.registered).isEqualTo(1);
        assertThat(brokerClient.refreshed).isZero();
    }

    private RegistrationLifecycle lifecycle(
            BrokerClient brokerClient, ConnectionRegistry connectionRegistry) {
        return new RegistrationLifecycle(
                "gw-1", "127.0.0.1", 12201, brokerClient, connectionRegistry);
    }

    private static class CapturingBrokerClient extends BrokerClient {
        protected GatewayRegisterParams registerGatewayCommand;
        protected GatewayHeartbeatParams heartbeatGatewayCommand;
        protected ConnectionSyncParams syncConnectionsCommand;
        protected String events = "";

        @Override
        public void registerGateway(GatewayRegisterParams params) {
            this.registerGatewayCommand = params;
            appendEvent("register");
        }

        @Override
        public void syncConnections(ConnectionSyncParams params) {
            this.syncConnectionsCommand = params;
            appendEvent("sync");
        }

        @Override
        public void heartbeatGateway(GatewayHeartbeatParams params) {
            this.heartbeatGatewayCommand = params;
            appendEvent("heartbeat");
        }

        @Override
        public void registerConnection(ConnectionRegisterParams params) {
        }

        @Override
        public void unregisterConnection(ConnectionUnregisterParams params) {
        }

        @Override
        public BrokerFrameWriteResult writeFrame(BrokerFrameWriteParams params) {
            return BrokerFrameWriteResult.ok();
        }

        protected void appendEvent(String event) {
            if (!events.isEmpty()) {
                events += ",";
            }
            events += event;
        }
    }

    private static class FailingBrokerClient extends CapturingBrokerClient {
        private int registered;
        private int refreshed;

        @Override
        public void registerGateway(GatewayRegisterParams params) {
            registered++;
            throw new IllegalStateException("broker unavailable");
        }

        @Override
        public void syncConnections(ConnectionSyncParams params) {
            refreshed++;
        }
    }

    private static class FailOnceBrokerClient extends CapturingBrokerClient {
        private boolean failed;

        @Override
        public void registerGateway(GatewayRegisterParams params) {
            appendEvent("register");
            if (!failed) {
                failed = true;
                throw new IllegalStateException("broker unavailable");
            }
            this.registerGatewayCommand = params;
        }
    }
}
