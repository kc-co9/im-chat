package com.co.kc.imchat.service.message.infrastructure.notification;

import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.common.model.enums.FrameType;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrokerMessageNotifierTest {

    @Test
    void notifiesReceiverThroughBroker() {
        MessagePushService pushService = new MessagePushService(new BrokerClient(
                new StubBoltInvoker(List.of("conn-1")), "127.0.0.1:12200", 3000));
        BrokerMessageNotifier notifier = new BrokerMessageNotifier(pushService);
        FrameResponse frame = new FrameResponse("1", FrameType.PUSH, "message.private.sent", null,
                "trace", null, null, Map.of("messageId", 1L, "eventId", "event-1", "receiptId", "receipt-1"));

        BrokerFrameWriteResult response = notifier.notify(2L, frame);

        assertEquals(2L, response.userId());
        assertEquals(List.of("conn-1"), response.acceptedConnectionIds());
    }

    private record StubBoltInvoker(List<String> acceptedConnectionIds) implements BoltInvoker {
        @Override
        @SuppressWarnings("unchecked")
        public <T, R> R invoke(String address, String service, String operation, T request,
                               Class<R> responseType, int timeoutMillis) {
            BrokerFrameWriteParams params = (BrokerFrameWriteParams) request;
            return (R) BrokerFrameWriteResult.writeResult(params.userId(), acceptedConnectionIds, List.of());
        }
    }
}
