package com.co.kc.imchat.interfaces.endpoint.consumer;

import com.co.kc.imchat.application.model.notification.ImGroupRevokedNotification;
import com.co.kc.imchat.application.model.enums.RedisTopic;
import com.co.kc.imchat.application.support.redis.RedisSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import static com.co.kc.imchat.interfaces.support.websocket.PushQueue.QUEUE_GROUP_MESSAGE_REVOKED;

@Component
@RequiredArgsConstructor
public class ImGroupMessageRevokedConsumer implements RedisSubscriber<ImGroupRevokedNotification> {
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public RedisTopic topic() {
        return RedisTopic.GROUP_MESSAGE_REVOKE;
    }

    @Override
    public void onMessage(ImGroupRevokedNotification message) {
        messagingTemplate.convertAndSendToUser(String.valueOf(message.receiverId()), QUEUE_GROUP_MESSAGE_REVOKED, message);
    }

}
