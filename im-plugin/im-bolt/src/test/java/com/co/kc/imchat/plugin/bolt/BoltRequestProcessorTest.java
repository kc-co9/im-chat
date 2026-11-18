package com.co.kc.imchat.plugin.bolt;

import com.co.kc.imchat.plugin.bolt.model.enums.BoltTransportMode;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import com.co.kc.imchat.plugin.bolt.server.BoltRequestProcessor;
import com.co.kc.imchat.plugin.bolt.model.BoltRequest;
import com.co.kc.imchat.plugin.bolt.model.BoltResponse;
import com.co.kc.imchat.plugin.bolt.model.BoltRequestContext;
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
            public Object handle(BoltRequestContext context, String payload) {
                assertThat(context).isNotNull();
                return new PingResponse("pong:" + payload);
            }
        };
        com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties.Server policy = new com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties.Server();
        policy.setMode(BoltTransportMode.DEVELOPMENT_PLAINTEXT);
        policy.getDevelopment().setSyntheticData(true);
        BoltRequestProcessor processor = new BoltRequestProcessor(List.of(handler), policy);
        com.alipay.remoting.BizContext transport = org.mockito.Mockito.mock(com.alipay.remoting.BizContext.class);
        com.alipay.remoting.Connection connection = org.mockito.Mockito.mock(com.alipay.remoting.Connection.class);
        org.mockito.Mockito.when(transport.getConnection()).thenReturn(connection);
        org.mockito.Mockito.when(transport.getRemoteHost()).thenReturn("127.0.0.1");
        org.mockito.Mockito.when(connection.getChannel()).thenReturn(new io.netty.channel.embedded.EmbeddedChannel());

        BoltResponse response = (BoltResponse) processor.handleRequest(transport,
                new BoltRequest("test.service", "ping", "{\"value\":\"hello\"}"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("{\"value\":\"pong:{\\\"value\\\":\\\"hello\\\"}\"}");
    }

    @Test
    void returnsFailedResponseWhenHandlerDoesNotExist() {
        BoltRequestProcessor processor = new BoltRequestProcessor(List.of(), new ImBoltProperties.Server());

        BoltResponse response = (BoltResponse) processor.handleRequest(null,
                new BoltRequest("test.service", "missing", "{}"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("BOLT_HANDLER_NOT_FOUND");
    }

    @Test
    void registersProcessorForBoltRequestClass() {
        BoltRequestProcessor processor = new BoltRequestProcessor(List.of(), new ImBoltProperties.Server());

        assertThat(processor.interest()).isEqualTo(BoltRequest.class.getName());
    }

    private record PingResponse(String value) {
    }
}
