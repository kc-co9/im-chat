package com.co.kc.imchat.broker.support.event.publisher;

import com.co.kc.imchat.broker.support.event.model.BrokerEvent;

import java.util.List;

public interface BrokerEventPublisher {

    void publish(BrokerEvent event);

    void publish(List<BrokerEvent> eventList);
}
