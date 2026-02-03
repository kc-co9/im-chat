package com.kim.omgchat.endpoint.consumer;

import com.kim.omgchat.model.cqrs.command.notify.ImGroupSentNotifyCmd;
import com.kim.omgchat.model.enums.RedisTopic;
import com.kim.omgchat.support.redis.RedisSubscriber;
import org.springframework.stereotype.Component;

@Component
public class ImGroupMessageSentConsumer implements RedisSubscriber<ImGroupSentNotifyCmd> {
    @Override
    public RedisTopic topic() {
        return RedisTopic.GROUP_MESSAGE;
    }

    @Override
    public void onMessage(ImGroupSentNotifyCmd message) {

    }

}
