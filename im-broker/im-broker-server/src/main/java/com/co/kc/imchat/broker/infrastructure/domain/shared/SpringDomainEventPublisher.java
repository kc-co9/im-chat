package com.co.kc.imchat.broker.infrastructure.domain.shared;

import com.co.kc.imchat.common.domain.shared.event.DomainEventPublisher;
import com.co.kc.imchat.common.domain.shared.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

/** 将公共领域事件发布契约适配到 Spring 事件机制，批量事件按列表顺序逐个发布。 */
@Component
@RequiredArgsConstructor
public class SpringDomainEventPublisher implements DomainEventPublisher {
    private final ApplicationContext applicationContext;

    @Override
    public void publish(DomainEvent event) {
        applicationContext.publishEvent(event);
    }

    @Override
    public void publish(List<DomainEvent> events) {
        events.forEach(applicationContext::publishEvent);
    }
}
