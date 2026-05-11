package com.co.kc.imchat.infrastructure.support.notifier;

import com.co.kc.imchat.model.cqrs.command.notify.ImGroupRevokedNotifyCmd;
import com.co.kc.imchat.model.enums.RedisTopic;
import com.co.kc.imchat.support.notifier.ImMessageConfirmable;
import com.co.kc.imchat.support.notifier.task.NotifierTaskType;
import com.co.kc.imchat.support.redis.RedisPublisher;
import org.springframework.stereotype.Component;

@Component
public class GroupRevokedNotifier extends AbstractRedisImMessageNotifier<ImGroupRevokedNotifyCmd>
        implements ImMessageConfirmable {

    public GroupRevokedNotifier(RedisPublisher redisPublisher) {
        super(redisPublisher);
    }

    @Override
    protected RedisTopic topic() {
        return RedisTopic.GROUP_MESSAGE_REVOKE;
    }

    @Override
    public NotifierTaskType task() {
        return NotifierTaskType.GROUP_MESSAGE_REVOKE;
    }
}
