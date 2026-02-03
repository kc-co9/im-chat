package com.kim.omgchat.infrastructure.support;

import com.kim.omgchat.domain.shared.DomainEvent;
import com.kim.omgchat.support.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SpringEventPublisher implements DomainEventPublisher {
    private final ApplicationContext applicationContext;

    @Override
    public void publish(DomainEvent event) {
        applicationContext.publishEvent(event);
    }

    @Override
    public void publish(List<DomainEvent> eventList) {
        for (DomainEvent event : eventList) {
            applicationContext.publishEvent(event);
        }
    }
}
