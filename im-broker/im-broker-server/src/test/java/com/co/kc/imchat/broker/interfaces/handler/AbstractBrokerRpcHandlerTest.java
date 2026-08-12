package com.co.kc.imchat.broker.interfaces.handler;

import com.co.kc.imchat.broker.sdk.enums.BrokerBoltOperation;
import com.co.kc.imchat.common.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractBrokerRpcHandlerTest {

    @Test
    void handleDeserializesPayloadByGenericInputType() throws Exception {
        TestRpcHandler handler = new TestRpcHandler();
        TestParams params = new TestParams("gw-1", 1001L);

        String result = handler.handle(JsonUtils.toJson(params));

        assertThat(result).isEqualTo("gw-1:1001");
        assertThat(handler.params).isEqualTo(params);
    }

    private static class TestRpcHandler extends AbstractBrokerRpcHandler<TestParams, String> {
        private TestParams params;

        private TestRpcHandler() {
            super(BrokerBoltOperation.LIST_BROKERS);
        }

        @Override
        protected String process(TestParams params) {
            this.params = params;
            return params.gatewayId() + ":" + params.userId();
        }
    }

    private record TestParams(String gatewayId, Long userId) {
    }
}
