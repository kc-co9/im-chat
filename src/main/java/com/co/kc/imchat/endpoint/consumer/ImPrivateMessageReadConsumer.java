package com.co.kc.imchat.endpoint.consumer;

import com.co.kc.imchat.model.cqrs.command.notify.ImPrivateReadNotifyCmd;
import com.co.kc.imchat.model.enums.RedisTopic;
import com.co.kc.imchat.support.redis.RedisSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import static com.co.kc.imchat.model.enums.PushQueue.QUEUE_PRIVATE_MESSAGE_READ;

@Component
@RequiredArgsConstructor
public class ImPrivateMessageReadConsumer implements RedisSubscriber<ImPrivateReadNotifyCmd> {
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public RedisTopic topic() {
        return RedisTopic.PRIVATE_MESSAGE_READ;
    }

    @Override
    public void onMessage(ImPrivateReadNotifyCmd message) {
        messagingTemplate.convertAndSendToUser(String.valueOf(message.getReceiverId()), QUEUE_PRIVATE_MESSAGE_READ, message);

    }
}
