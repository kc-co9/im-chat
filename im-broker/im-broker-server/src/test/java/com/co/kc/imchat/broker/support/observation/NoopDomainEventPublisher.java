package com.co.kc.imchat.broker.support.observation;

import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import com.co.kc.imchat.common.domain.shared.event.DomainEvent;

import java.util.List;

/**
 * 测试用 Broker 事件发布器。
 */
public class NoopDomainEventPublisher implements DomainEventPublisher {

    @Override
    public void publish(DomainEvent event) {
    }

    @Override
    public void publish(List<DomainEvent> events) {
    }
}
