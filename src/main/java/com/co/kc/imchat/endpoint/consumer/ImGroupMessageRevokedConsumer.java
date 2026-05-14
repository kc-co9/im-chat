package com.co.kc.imchat.endpoint.consumer;

import com.co.kc.imchat.model.cqrs.command.group.GroupRevokedNotifyCmd;
import com.co.kc.imchat.model.enums.RedisTopic;
import com.co.kc.imchat.support.redis.RedisSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import static com.co.kc.imchat.model.enums.PushQueue.QUEUE_GROUP_MESSAGE_REVOKED;

@Component
@RequiredArgsConstructor
public class ImGroupMessageRevokedConsumer implements RedisSubscriber<GroupRevokedNotifyCmd> {
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public RedisTopic topic() {
        return RedisTopic.GROUP_MESSAGE_REVOKE;
    }

    @Override
    public void onMessage(GroupRevokedNotifyCmd message) {
        messagingTemplate.convertAndSendToUser(String.valueOf(message.getReceiverId()), QUEUE_GROUP_MESSAGE_REVOKED, message);
    }

}
