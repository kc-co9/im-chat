package com.co.kc.imchat.infrastructure.support.notifier;

import com.co.kc.imchat.model.cqrs.command.group.GroupSentNotifyCmd;
import com.co.kc.imchat.model.enums.RedisTopic;
import com.co.kc.imchat.support.notifier.ImMessageConfirmable;
import com.co.kc.imchat.support.notifier.task.NotifierTaskType;
import com.co.kc.imchat.support.redis.RedisPublisher;
import org.springframework.stereotype.Component;

@Component
public class GroupSentNotifier extends AbstractRedisImMessageNotifier<GroupSentNotifyCmd>
        implements ImMessageConfirmable {

    public GroupSentNotifier(RedisPublisher redisPublisher) {
        super(redisPublisher);
    }

    @Override
    protected RedisTopic topic() {
        return RedisTopic.GROUP_MESSAGE_SEND;
    }

    @Override
    public NotifierTaskType task() {
        return NotifierTaskType.GROUP_MESSAGE_SEND;
    }
}
