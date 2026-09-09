package com.co.kc.imchat.plugin.mq.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.binder.kafka.KafkaMessageChannelBinder;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaBinderRuntimeTest {

    @Test
    void providesSpringCloudStreamKafkaBinderRuntime() {
        assertThat(KafkaMessageChannelBinder.class).isNotNull();
    }
}
