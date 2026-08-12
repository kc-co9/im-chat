package com.co.kc.imchat.plugin.bolt;

import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.plugin.bolt.core.BoltRequestProcessor;
import com.co.kc.imchat.plugin.bolt.model.BoltRequest;
import com.co.kc.imchat.plugin.bolt.model.BoltResponse;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BoltRequestProcessorTest {

    @Test
    void dispatchesRequestToMatchedHandler() {
        BoltRequestHandler handler = new BoltRequestHandler() {
            @Override
            public String service() {
                return "test.service";
            }

            @Override
            public String operation() {
                return "ping";
            }

            @Override
            public Object handle(String payload) {
                return new PingResponse("pong:" + payload);
            }
        };
        BoltRequestProcessor processor = new BoltRequestProcessor(List.of(handler));

        BoltResponse response = (BoltResponse) processor.handleRequest(null,
                new BoltRequest("test.service", "ping", "{\"value\":\"hello\"}"));

        assertThat(response.success()).isTrue();
        assertThat(JsonUtils.fromJson(response.data(), PingResponse.class).value())
                .isEqualTo("pong:{\"value\":\"hello\"}");
    }

    @Test
    void returnsFailedResponseWhenHandlerDoesNotExist() {
        BoltRequestProcessor processor = new BoltRequestProcessor(List.of());

        BoltResponse response = (BoltResponse) processor.handleRequest(null,
                new BoltRequest("test.service", "missing", "{}"));

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo("BOLT_HANDLER_NOT_FOUND");
    }

    private record PingResponse(String value) {
    }
}
