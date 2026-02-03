package com.kim.omgchat.support.redis;

import com.kim.omgchat.model.enums.RedisTopic;

public interface RedisPublisher {

    /**
     * 广播消息到指定频道
     */
    void publish(RedisTopic topic, Object message);
}
