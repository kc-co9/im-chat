package com.co.kc.imchat.support.redis;

import com.co.kc.imchat.model.enums.RedisTopic;

public interface RedisPublisher {

    /**
     * 广播消息到指定频道
     */
    void publish(RedisTopic topic, Object message);
}
