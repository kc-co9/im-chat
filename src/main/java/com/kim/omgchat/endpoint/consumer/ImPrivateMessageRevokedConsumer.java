package com.kim.omgchat.endpoint.consumer;

import com.kim.omgchat.model.cqrs.command.notify.ImPrivateRevokedNotifyCmd;
import com.kim.omgchat.model.cqrs.command.notify.ImPrivateSentNotifyCmd;
import com.kim.omgchat.model.enums.RedisTopic;
import com.kim.omgchat.support.redis.RedisSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import static com.kim.omgchat.model.enums.PushQueue.QUEUE_PRIVATE_MESSAGE_READ;
import static com.kim.omgchat.model.enums.PushQueue.QUEUE_PRIVATE_MESSAGE_REVOKED;

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
