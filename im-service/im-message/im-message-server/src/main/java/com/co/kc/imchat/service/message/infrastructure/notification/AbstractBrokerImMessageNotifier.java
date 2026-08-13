package com.co.kc.imchat.service.message.infrastructure.notification;

import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.common.model.enums.FrameType;
import com.co.kc.imchat.service.message.application.notification.ImMessageNotifier;
import com.co.kc.imchat.service.message.application.notification.confirmable.ImMessageConfirmable;
import com.co.kc.imchat.service.message.application.notification.task.ReceiptType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractBrokerImMessageNotifier<T> implements ImMessageNotifier<T>, ImMessageConfirmable<T> {

    private final BrokerMessageNotifier brokerMessageNotifier;

    @Override
    public void notify(T notification) {
        Long receiverId = receiverId(notification);
        BrokerFrameWriteResult result = brokerMessageNotifier.notify(receiverId, new FrameResponse(
                "1",
                FrameType.PUSH,
                command(),
                null,
                null,
                null,
                null,
                pushBody(notification)
        ));
        if (!result.processed() || !result.failedConnectionIds().isEmpty()) {
            log.warn("message notification was not delivered, receiverId:{}, cmd:{}, receiptId:{}, processed:{}, code:{}, message:{}, failedConnections:{}",
                    receiverId, command(), receiptId(notification), result.processed(), result.code(), result.message(),
                    result.failedConnectionIds());
            return;
        }
        if (result.acceptedConnectionIds().isEmpty()) {
            log.debug("message notification receiver is offline, receiverId:{}, cmd:{}, receiptId:{}",
                    receiverId, command(), receiptId(notification));
        }
    }

    protected abstract String command();

    protected abstract Long receiverId(T notification);

    protected String eventId(T notification) {
        return receiptId(notification);
    }

    protected Map<String, Object> body(T notification) {
        return Map.of("notification", notification);
    }

    private Map<String, Object> pushBody(T notification) {
        Map<String, Object> body = new LinkedHashMap<>(body(notification));
        String eventId = eventId(notification);
        String receiptId = receiptId(notification);
        if (eventId != null) {
            body.put("eventId", eventId);
        }
        if (receiptId != null) {
            body.put("receiptId", receiptId);
        }
        return body;
    }

    @Override
    public abstract ReceiptType receiptType();
}
