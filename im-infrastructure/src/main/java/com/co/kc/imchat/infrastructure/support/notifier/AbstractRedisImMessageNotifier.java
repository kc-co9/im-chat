package com.co.kc.imchat.infrastructure.support.notifier;

import com.co.kc.imchat.application.model.enums.RedisTopic;
import com.co.kc.imchat.application.support.notifier.ImMessageNotifier;
import com.co.kc.imchat.application.support.redis.RedisPublisher;

public abstract class AbstractRedisImMessageNotifier<T> implements ImMessageNotifier<T> {

    private final RedisPublisher redisPublisher;

    protected AbstractRedisImMessageNotifier(RedisPublisher redisPublisher) {
        this.redisPublisher = redisPublisher;
    }

    @Override
    public void notify(T command) {
        redisPublisher.publish(topic(), command);
    }

    protected abstract RedisTopic topic();
}
