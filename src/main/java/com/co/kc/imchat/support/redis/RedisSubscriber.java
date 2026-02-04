package com.co.kc.imchat.support.redis;

import com.co.kc.imchat.model.enums.RedisTopic;

/**
 * Redis订阅器
 */
public interface RedisSubscriber<T> {

    /**
     * 订阅的 Redis Topic
     */
    RedisTopic topic();

    /**
     * 接收到消息
     */
    void onMessage(T message);

}
