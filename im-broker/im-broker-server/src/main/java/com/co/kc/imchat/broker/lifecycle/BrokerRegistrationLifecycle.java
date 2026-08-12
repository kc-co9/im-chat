package com.co.kc.imchat.broker.lifecycle;

import com.co.kc.imchat.broker.config.properties.BrokerProperties;
import com.co.kc.imchat.broker.domain.registry.broker.BrokerRegistry;
import com.co.kc.imchat.broker.support.event.model.BrokerHeartbeatEvent;
import com.co.kc.imchat.broker.support.event.model.BrokerRegisteredEvent;
import com.co.kc.imchat.broker.support.event.model.BrokerRemovedEvent;
import com.co.kc.imchat.broker.support.event.publisher.BrokerEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Broker 实例注册生命周期。
 * <p>
 * 应用启动后将当前 broker 写入本机注册表，之后定时刷新当前 broker 心跳；
 * 状态变化会通过事件监听器写入 gossip 状态表，再同步给其他 broker。
 */
@Slf4j
@Component
public class BrokerRegistrationLifecycle {
    private static final long HEARTBEAT_DELAY_MILLIS = 30000L;

    private final BrokerRegistry brokerRegistry;
    private final BrokerEventPublisher brokerEventPublisher;
    private final BrokerProperties brokerProperties;

    public BrokerRegistrationLifecycle(
            BrokerRegistry brokerRegistry,
            BrokerEventPublisher brokerEventPublisher,
            BrokerProperties brokerProperties) {
        this.brokerRegistry = brokerRegistry;
        this.brokerEventPublisher = brokerEventPublisher;
        this.brokerProperties = brokerProperties;
    }

    /**
     * 应用启动完成后注册当前 broker。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        BrokerProperties.Instance instance = brokerProperties.getInstance();
        brokerRegistry.register(instance.getId(), instance.getHost(), instance.getPort());
        brokerEventPublisher.publish(new BrokerRegisteredEvent(
                instance.getId(), instance.getHost(), instance.getPort()));
    }

    /**
     * 定时刷新本机 broker 心跳。
     */
    @Scheduled(fixedDelay = HEARTBEAT_DELAY_MILLIS)
    public void heartbeat() {
        String brokerId = brokerProperties.getInstance().getId();
        try {
            brokerRegistry.heartbeat(brokerId);
            brokerEventPublisher.publish(new BrokerHeartbeatEvent(brokerId));
        } catch (RuntimeException ex) {
            log.warn("failed to refresh broker heartbeat, will retry later, brokerId:{}, error:{}",
                    brokerId, ex.toString());
        }
    }

    /**
     * 应用关闭时注销当前 broker，并发布 broker 移除事件。
     */
    @EventListener(ContextClosedEvent.class)
    public void onClosed() {
        String brokerId = brokerProperties.getInstance().getId();
        try {
            brokerRegistry.unregister(brokerId);
            brokerEventPublisher.publish(new BrokerRemovedEvent(brokerId));
        } catch (RuntimeException ex) {
            log.warn("failed to unregister broker, brokerId:{}, error:{}", brokerId, ex.toString());
        }
    }
}
