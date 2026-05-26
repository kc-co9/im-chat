package com.co.kc.imchat.infrastructure.config;

import com.co.kc.imchat.application.model.notification.ImPrivateSentNotification;
import com.co.kc.imchat.application.model.enums.RedisTopic;
import com.co.kc.imchat.domain.message.model.ImMessageTypeEnum;
import com.co.kc.imchat.application.support.redis.RedisSubscriber;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConfigTest {

    @Test
    void subscriberSerializerReadsPublishedObjectJson() {
        RedisConfig redisConfig = new RedisConfig();
        RedisSerializer<?> serializer = redisConfig.redisSerializer(new PrivateSentSubscriber());

        ImPrivateSentNotification notification =
                new ImPrivateSentNotification(1L, 2L, 3L, 4L, ImMessageTypeEnum.TEXT, "hello", LocalDateTime.now());

        byte[] bytes = redisConfig.redisMessageSerializer().serialize(notification);
        Object result = serializer.deserialize(bytes);

        assertThat(result).isInstanceOf(ImPrivateSentNotification.class);
        assertThat(((ImPrivateSentNotification) result).messageContent()).isEqualTo("hello");
    }

    private static class PrivateSentSubscriber implements RedisSubscriber<ImPrivateSentNotification> {
        @Override
        public RedisTopic topic() {
            return RedisTopic.PRIVATE_MESSAGE_SEND;
        }

        @Override
        public void onMessage(ImPrivateSentNotification message) {
        }
    }
}
