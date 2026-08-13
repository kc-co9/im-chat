package com.co.kc.imchat.service.message.infrastructure.notification;

import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.common.model.io.FrameResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class AbstractBrokerImMessageNotifierTest {

    @Test
    void logsFailedDeliveryWithBusinessContext(CapturedOutput output) {
        TestNotifier notifier = new TestNotifier(new StubBrokerMessageNotifier(
                BrokerFrameWriteResult.failed("BROKER_UNAVAILABLE", "broker unavailable")));

        notifier.notify(new TestNotification(2L, "receipt-1"));

        assertThat(output).contains("message notification was not delivered")
                .contains("receiverId:2")
                .contains("cmd:message.test")
                .contains("receiptId:receipt-1");
    }

    @Test
    void doesNotWarnWhenReceiverHasNoActiveConnection(CapturedOutput output) {
        TestNotifier notifier = new TestNotifier(new StubBrokerMessageNotifier(
                BrokerFrameWriteResult.writeResult(2L, java.util.List.of())));

        notifier.notify(new TestNotification(2L, "receipt-1"));

        assertThat(output).doesNotContain("WARN")
                .doesNotContain("message notification was not delivered");
    }

    private record TestNotification(Long receiverId, String receiptId) {
    }

    private static class TestNotifier extends AbstractBrokerImMessageNotifier<TestNotification> {
        TestNotifier(BrokerMessageNotifier brokerMessageNotifier) {
            super(brokerMessageNotifier);
        }

        @Override
        protected String command() {
            return "message.test";
        }

        @Override
        protected Long receiverId(TestNotification notification) {
            return notification.receiverId();
        }

        @Override
        public com.co.kc.imchat.service.message.application.notification.task.ReceiptType receiptType() {
            return com.co.kc.imchat.service.message.application.notification.task.ReceiptType.PRIVATE_MESSAGE_SEND;
        }

        @Override
        public String receiptId(TestNotification notification) {
            return notification.receiptId();
        }
    }

    private static class StubBrokerMessageNotifier extends BrokerMessageNotifier {
        private final BrokerFrameWriteResult result;

        StubBrokerMessageNotifier(BrokerFrameWriteResult result) {
            super(null);
            this.result = result;
        }

        @Override
        public BrokerFrameWriteResult notify(Long receiverId, FrameResponse frame) {
            return result;
        }
    }
}
