package com.co.kc.imchat.gateway.ws;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayHeartbeatParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionUnregisterParams;
import com.co.kc.imchat.gateway.ws.lifecycle.RegistrationLifecycle;
import com.co.kc.imchat.gateway.ws.routing.GatewayRouteSnapshotSync;
import com.co.kc.imchat.gateway.ws.support.BrokerClientTestSupport;
import com.co.kc.imchat.common.model.enums.ServiceName;
import com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

class RegistrationLifecycleTest {

    @Test
    void startupRegistersGatewayAndSyncsConnectionRoutes() throws Exception {
        CapturingBrokerClient brokerClient = new CapturingBrokerClient();
        RegistrationLifecycle lifecycle = lifecycle(brokerClient);

        lifecycle.onReady();

        assertThat(brokerClient.registerGatewayCommand)
                .isEqualTo(new GatewayRegisterParams("gw-1", "127.0.0.1", 12201));
        assertThat(brokerClient.snapshotSyncs).isEqualTo(1);
    }

    @Test
    void scheduledRefreshRegistersGatewayBeforeSyncingConnectionRoutes() {
        CapturingBrokerClient brokerClient = new CapturingBrokerClient();
        RegistrationLifecycle lifecycle = lifecycle(brokerClient);

        lifecycle.onReady();
        brokerClient.events = "";
        lifecycle.refresh();

        assertThat(brokerClient.events).isEqualTo("register,sync");
        assertThat(brokerClient.registerGatewayCommand)
                .isEqualTo(new GatewayRegisterParams("gw-1", "127.0.0.1", 12201));
    }

    @Test
    void scheduledRefreshRegistersGatewayWhenStartupRegistrationFailed() {
        FailOnceBrokerClient brokerClient = new FailOnceBrokerClient();
        RegistrationLifecycle lifecycle = lifecycle(brokerClient);

        lifecycle.onReady();
        lifecycle.refresh();

        assertThat(brokerClient.events).isEqualTo("register,register,sync");
    }

    @Test
    void startupSkipsFailureWhenBrokerIsTemporarilyUnavailable() {
        FailingBrokerClient brokerClient = new FailingBrokerClient();
        RegistrationLifecycle lifecycle = lifecycle(brokerClient);

        assertThatCode(lifecycle::onReady).doesNotThrowAnyException();

        assertThat(brokerClient.registered).isEqualTo(1);
        assertThat(brokerClient.snapshotSyncs).isZero();
    }

    private RegistrationLifecycle lifecycle(CapturingBrokerClient brokerClient) {
        GatewayRouteSnapshotSync snapshotSync = () -> {
            brokerClient.snapshotSyncs++;
            brokerClient.appendEvent("sync");
        };
        return new RegistrationLifecycle(
                "gw-1", "127.0.0.1", 12201, brokerClient, snapshotSync);
    }

    private static class CapturingBrokerClient extends BrokerClient {
        protected GatewayRegisterParams registerGatewayCommand;
        protected GatewayHeartbeatParams heartbeatGatewayCommand;
        protected int snapshotSyncs;
        protected String events = "";

        private CapturingBrokerClient() {
            super(BrokerClientTestSupport.invoker(), BrokerClientTestSupport.discovery(),
                    ServiceName.IM_BROKER, BrokerLoadBalance.HASH, 3000);
        }

        @Override
        public void registerGateway(GatewayRegisterParams params) {
            this.registerGatewayCommand = params;
            appendEvent("register");
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
        @Override
        public void registerGateway(GatewayRegisterParams params) {
            registered++;
            throw new IllegalStateException("broker unavailable");
        }

        private FailingBrokerClient() {
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
