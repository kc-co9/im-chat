package com.co.kc.imchat.service.message.infrastructure.config.beans;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.service.message.infrastructure.notification.BrokerMessageNotifier;
import com.co.kc.imchat.service.message.infrastructure.notification.MessagePushService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RealtimeBeans {

    @Bean
    public MessagePushService messagePushService(BrokerClient brokerClient) {
        return new MessagePushService(brokerClient);
    }

    @Bean
    public BrokerMessageNotifier brokerMessageNotifier(MessagePushService messagePushService) {
        return new BrokerMessageNotifier(messagePushService);
    }
}
