package com.co.kc.imchat.plugin.mq;

import com.co.kc.imchat.plugin.mq.model.MqMessage;
import com.co.kc.imchat.plugin.mq.spi.MessagePublisher;
import com.co.kc.imchat.plugin.mq.spi.MessageSubscriber;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImMqAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ImMqAutoConfiguration.class);

    @Test
    void memoryMessageBusIsDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(MessagePublisher.class);
            assertThat(context).doesNotHaveBean(MessageSubscriber.class);
        });
    }

    @Test
    void memoryMessageBusCanPublishToSubscribers() {
        contextRunner
                .withPropertyValues("im.mq.memory.enabled=true")
                .run(context -> {
                    MessagePublisher publisher = context.getBean(MessagePublisher.class);
                    MessageSubscriber subscriber = context.getBean(MessageSubscriber.class);
                    List<String> received = new ArrayList<>();

                    subscriber.subscribe("chat", String.class, received::add);
                    publisher.publish(new MqMessage<>("chat", "hello"));

                    assertThat(received).containsExactly("hello");
                });
    }
}
