package com.co.kc.imchat.plugin.mq.spi;

import java.util.function.Consumer;

/**
 * 消息订阅器。
 * <p>
 * 统一封装 MQ 订阅能力，业务侧通过类型化 handler 消费消息。
 */
public interface MessageSubscriber {

    /**
     * 订阅指定主题。
     *
     * @param topic       主题
     * @param payloadType 消息体类型
     * @param handler     消息处理器
     * @param <T>         消息体类型
     */
    <T> void subscribe(String topic, Class<T> payloadType, Consumer<T> handler);
}
