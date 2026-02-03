package com.kim.omgchat.infrastructure.support;

import com.kim.omgchat.model.cqrs.command.notify.ImGroupSentNotifyCmd;
import com.kim.omgchat.model.cqrs.command.notify.ImPrivateSentNotifyCmd;
import com.kim.omgchat.model.cqrs.command.notify.ImPrivateReadNotifyCmd;
import com.kim.omgchat.model.cqrs.command.notify.ImPrivateRevokedNotifyCmd;
import com.kim.omgchat.model.enums.RedisTopic;
import com.kim.omgchat.support.ImMessageNotifier;
import com.kim.omgchat.support.redis.RedisPublisher;
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

    }
}
