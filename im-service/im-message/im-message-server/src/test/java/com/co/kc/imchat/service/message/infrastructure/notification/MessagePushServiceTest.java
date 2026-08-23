package com.co.kc.imchat.service.message.infrastructure.notification;

import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.common.model.enums.FrameType;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.service.message.support.BrokerClientTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagePushServiceTest {

    @Test
    void pushesThroughBrokerClient() {
        MessagePushService service = new MessagePushService(BrokerClientTestSupport.client(
                new StubBoltInvoker(BrokerFrameWriteResult.writeResult(1L, List.of("conn-1"), List.of()))));

        BrokerFrameWriteResult response = service.push(1L,
                new FrameResponse("1", FrameType.PUSH, "message.private.sent", null, "trace",
                        null, null, Map.of("eventId", "event-1", "receiptId", "receipt-1")));

        assertEquals(1L, response.userId());
        assertEquals(List.of("conn-1"), response.acceptedConnectionIds());
    }

    private record StubBoltInvoker(BrokerFrameWriteResult response) implements BoltInvoker {
        @Override
        @SuppressWarnings("unchecked")
        public <T, R> R invoke(String address, String service, String operation, T request,
                               Class<R> responseType, int timeoutMillis) {
            BrokerFrameWriteParams params = (BrokerFrameWriteParams) request;
            return (R) BrokerFrameWriteResult.writeResult(params.userId(), response.acceptedConnectionIds(),
                    response.failedConnectionIds());
        }
    }
}
