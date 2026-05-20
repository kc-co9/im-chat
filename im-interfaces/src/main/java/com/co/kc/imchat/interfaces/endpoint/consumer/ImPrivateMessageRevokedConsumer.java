package com.co.kc.imchat.interfaces.endpoint.consumer;

import com.co.kc.imchat.application.model.cqrs.command.notify.ImPrivateRevokedNotifyCmd;
import com.co.kc.imchat.application.model.enums.RedisTopic;
import com.co.kc.imchat.application.support.redis.RedisSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import static com.co.kc.imchat.interfaces.support.websocket.PushQueue.QUEUE_PRIVATE_MESSAGE_REVOKED;

@Component
@RequiredArgsConstructor
public class ImPrivateMessageRevokedConsumer implements RedisSubscriber<ImPrivateRevokedNotifyCmd> {
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public RedisTopic topic() {
        return RedisTopic.PRIVATE_MESSAGE_REVOKE;
    }

    @Override
    public void onMessage(ImPrivateRevokedNotifyCmd message) {
        messagingTemplate.convertAndSendToUser(String.valueOf(message.getReceiverId()), QUEUE_PRIVATE_MESSAGE_REVOKED, message);
    }
}
