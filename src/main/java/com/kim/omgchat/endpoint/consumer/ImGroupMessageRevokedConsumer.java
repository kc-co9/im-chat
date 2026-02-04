package com.kim.omgchat.endpoint.consumer;

import com.kim.omgchat.model.cqrs.command.notify.ImGroupRevokedNotifyCmd;
import com.kim.omgchat.model.enums.RedisTopic;
import com.kim.omgchat.support.redis.RedisSubscriber;
import org.springframework.stereotype.Component;

@Component
public class ImGroupMessageRevokedConsumer implements RedisSubscriber<ImGroupRevokedNotifyCmd> {
    @Override
    public RedisTopic topic() {
        return RedisTopic.GROUP_MESSAGE_REVOKE;
    }

    @Override
    public void onMessage(ImGroupRevokedNotifyCmd message) {

    }

}
