package com.co.kc.imchat.plugin.mq.spi;

import com.co.kc.imchat.plugin.mq.model.MqMessage;

/**
 * 消息发布器。
 * <p>
 * 统一封装 MQ 发送能力，业务侧只关注主题和消息体。
 */
public interface MessagePublisher {

    /**
     * 发布一条 MQ 消息。
     *
     * @param message 待发布消息
     */
    void publish(MqMessage<?> message);
}
