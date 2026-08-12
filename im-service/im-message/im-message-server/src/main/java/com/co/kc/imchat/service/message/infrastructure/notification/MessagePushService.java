package com.co.kc.imchat.service.message.infrastructure.notification;

import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.common.model.io.FrameResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MessagePushService {
    private final BrokerClient brokerClient;

    public BrokerFrameWriteResult push(Long userId, FrameResponse frame) {
        return brokerClient.writeFrame(BrokerFrameWriteParams.outbound(userId, frame));
    }
}
