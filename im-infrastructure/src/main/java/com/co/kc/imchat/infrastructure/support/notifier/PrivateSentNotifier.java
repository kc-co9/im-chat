package com.co.kc.imchat.infrastructure.support.notifier;

import com.co.kc.imchat.application.model.cqrs.command.notify.ImPrivateSentNotifyCmd;
import com.co.kc.imchat.application.model.enums.RedisTopic;
import com.co.kc.imchat.application.support.notifier.task.NotifierTaskType;
import com.co.kc.imchat.application.support.notifier.ImMessageConfirmable;
import com.co.kc.imchat.application.support.redis.RedisPublisher;
import org.springframework.stereotype.Component;

@Component
public class PrivateSentNotifier extends AbstractRedisImMessageNotifier<ImPrivateSentNotifyCmd> implements ImMessageConfirmable {

    public PrivateSentNotifier(RedisPublisher redisPublisher) {
        super(redisPublisher);
    }

    @Override
    protected RedisTopic topic() {
        return RedisTopic.PRIVATE_MESSAGE_SEND;
    }

    @Override
    public NotifierTaskType task() {
        return NotifierTaskType.PRIVATE_MESSAGE_SEND;
    }
}
