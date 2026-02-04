package com.co.kc.imchat.infrastructure.support;

import com.co.kc.imchat.model.cqrs.command.notify.ImGroupRevokedNotifyCmd;
import com.co.kc.imchat.model.cqrs.command.notify.ImGroupSentNotifyCmd;
import com.co.kc.imchat.model.cqrs.command.notify.ImPrivateSentNotifyCmd;
import com.co.kc.imchat.model.cqrs.command.notify.ImPrivateReadNotifyCmd;
import com.co.kc.imchat.model.cqrs.command.notify.ImPrivateRevokedNotifyCmd;
import com.co.kc.imchat.model.enums.RedisTopic;
import com.co.kc.imchat.support.ImMessageNotifier;
import com.co.kc.imchat.support.redis.RedisPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisImMessageNotifier implements ImMessageNotifier {

    private final RedisPublisher redisPublisher;

    @Override
    public void notify(ImPrivateSentNotifyCmd command) {
        redisPublisher.publish(RedisTopic.PRIVATE_MESSAGE_SEND, command);
    }

    @Override
    public void notify(ImPrivateRevokedNotifyCmd command) {
        redisPublisher.publish(RedisTopic.PRIVATE_MESSAGE_REVOKE, command);
    }

    @Override
    public void notify(ImPrivateReadNotifyCmd command) {
        redisPublisher.publish(RedisTopic.PRIVATE_MESSAGE_READ, command);
    }

    @Override
    public void notify(ImGroupSentNotifyCmd command) {
        redisPublisher.publish(RedisTopic.GROUP_MESSAGE_SEND, command);
    }

    @Override
    public void notify(ImGroupRevokedNotifyCmd command) {
        redisPublisher.publish(RedisTopic.GROUP_MESSAGE_REVOKE, command);
    }
}
