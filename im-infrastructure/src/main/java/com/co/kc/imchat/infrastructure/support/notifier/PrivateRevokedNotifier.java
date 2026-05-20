package com.co.kc.imchat.infrastructure.support.notifier;

import com.co.kc.imchat.application.model.cqrs.command.notify.ImPrivateRevokedNotifyCmd;
import com.co.kc.imchat.application.model.enums.RedisTopic;
import com.co.kc.imchat.application.support.notifier.ImMessageConfirmable;
import com.co.kc.imchat.application.support.notifier.task.NotifierTaskType;
import com.co.kc.imchat.application.support.redis.RedisPublisher;
import org.springframework.stereotype.Component;

@Component
public class PrivateRevokedNotifier extends AbstractRedisImMessageNotifier<ImPrivateRevokedNotifyCmd> implements ImMessageConfirmable {

    public PrivateRevokedNotifier(RedisPublisher redisPublisher) {
        super(redisPublisher);
    }

    @Override
    protected RedisTopic topic() {
        return RedisTopic.PRIVATE_MESSAGE_REVOKE;
    }

    @Override
    public NotifierTaskType task() {
        return NotifierTaskType.PRIVATE_MESSAGE_REVOKE;
    }
}
