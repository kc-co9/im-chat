package com.co.kc.imchat.service.message.infrastructure.notification;

import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.common.model.io.FrameResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BrokerMessageNotifier {
    private final MessagePushService messagePushService;

    public BrokerFrameWriteResult notify(Long receiverId, FrameResponse frame) {
        return messagePushService.push(receiverId, frame);
    }
}
