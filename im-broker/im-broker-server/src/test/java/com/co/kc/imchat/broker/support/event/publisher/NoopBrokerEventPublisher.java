package com.co.kc.imchat.broker.support.event.publisher;

import com.co.kc.imchat.broker.support.event.model.BrokerEvent;

import java.util.List;

/**
 * 测试用 Broker 事件发布器。
 */
public class NoopBrokerEventPublisher implements BrokerEventPublisher {

    @Override
    public void publish(BrokerEvent event) {
    }

    @Override
    public void publish(List<BrokerEvent> eventList) {
    }
}
