package com.co.kc.imchat.interfaces.endpoint.consumer;

import com.co.kc.imchat.application.model.notification.ImPrivateSentNotification;
import com.co.kc.imchat.application.model.enums.RedisTopic;
import com.co.kc.imchat.application.support.redis.RedisSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import static com.co.kc.imchat.interfaces.support.websocket.PushQueue.QUEUE_PRIVATE_MESSAGE_SENT;

@Component
@RequiredArgsConstructor
public class ImPrivateMessageSentConsumer implements RedisSubscriber<ImPrivateSentNotification> {
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public RedisTopic topic() {
        return RedisTopic.PRIVATE_MESSAGE_SEND;
    }

    @Override
    public void onMessage(ImPrivateSentNotification message) {
        messagingTemplate.convertAndSendToUser(String.valueOf(message.receiverId()), QUEUE_PRIVATE_MESSAGE_SENT, message);
    }
}
