package com.co.kc.imchat.interfaces.endpoint.consumer;

import com.co.kc.imchat.application.model.cqrs.command.group.GroupSentNotifyCmd;
import com.co.kc.imchat.application.model.enums.RedisTopic;
import com.co.kc.imchat.application.support.redis.RedisSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import static com.co.kc.imchat.interfaces.support.websocket.PushQueue.QUEUE_GROUP_MESSAGE_SENT;

@Component
@RequiredArgsConstructor
public class ImGroupMessageSentConsumer implements RedisSubscriber<GroupSentNotifyCmd> {
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public RedisTopic topic() {
        return RedisTopic.GROUP_MESSAGE_SEND;
    }

    @Override
    public void onMessage(GroupSentNotifyCmd message) {
        messagingTemplate.convertAndSendToUser(String.valueOf(message.getReceiverId()), QUEUE_GROUP_MESSAGE_SENT, message);
    }

}
