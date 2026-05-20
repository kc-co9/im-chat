package com.co.kc.imchat.application.support.event;

import com.co.kc.imchat.domain.shared.event.DomainEvent;

import java.util.List;

/**
 * 领域事件发布器
 *
 * @author kc
 */
public interface DomainEventPublisher {
    /**
     * 发布事件
     *
     * @param event 领域事件
     */
    void publish(DomainEvent event);

    /**
     * 发布一系列事件
     *
     * @param eventList 领域事件列表
     */
    void publish(List<DomainEvent> eventList);

}
