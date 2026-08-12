package com.co.kc.imchat.plugin.mq;

import com.co.kc.imchat.plugin.mq.core.InMemoryMessageBus;
import com.co.kc.imchat.plugin.mq.spi.MessagePublisher;
import com.co.kc.imchat.plugin.mq.spi.MessageSubscriber;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ImMqAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean({MessagePublisher.class, MessageSubscriber.class})
    @ConditionalOnProperty(prefix = "im.mq.memory", name = "enabled", havingValue = "true")
    public InMemoryMessageBus inMemoryMessageBus() {
        return new InMemoryMessageBus();
    }
}
