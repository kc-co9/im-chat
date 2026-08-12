package com.co.kc.imchat.broker.support.event.publisher;

import com.co.kc.imchat.broker.support.event.model.BrokerEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SpringBrokerEventPublisher implements BrokerEventPublisher {
    private final ApplicationContext applicationContext;

    @Override
    public void publish(BrokerEvent event) {
        applicationContext.publishEvent(event);
    }

    @Override
    public void publish(List<BrokerEvent> eventList) {
        eventList.forEach(applicationContext::publishEvent);
    }
}
