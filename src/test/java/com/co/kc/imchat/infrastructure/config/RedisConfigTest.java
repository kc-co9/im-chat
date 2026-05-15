package com.co.kc.imchat.infrastructure.config;

import com.co.kc.imchat.model.cqrs.command.notify.ImPrivateSentNotifyCmd;
import com.co.kc.imchat.model.enums.ImMessageTypeEnum;
import com.co.kc.imchat.support.redis.RedisSubscriber;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConfigTest {

    @Test
    void subscriberSerializerReadsPublishedObjectJson() {
        RedisConfig redisConfig = new RedisConfig();
        RedisSerializer<?> serializer = redisConfig.redisSerializer(new PrivateSentSubscriber());

        ImPrivateSentNotifyCmd command = new ImPrivateSentNotifyCmd();
        command.setMessageId(1L);
        command.setChatId(2L);
        command.setSenderId(3L);
        command.setReceiverId(4L);
        command.setMessageType(ImMessageTypeEnum.TEXT);
        command.setMessageContent("hello");
        command.setSendTime(LocalDateTime.now());

        byte[] bytes = redisConfig.redisMessageSerializer().serialize(command);
        Object result = serializer.deserialize(bytes);

        assertThat(result).isInstanceOf(ImPrivateSentNotifyCmd.class);
        assertThat(((ImPrivateSentNotifyCmd) result).getMessageContent()).isEqualTo("hello");
    }

    private static class PrivateSentSubscriber implements RedisSubscriber<ImPrivateSentNotifyCmd> {
        @Override
        public com.co.kc.imchat.model.enums.RedisTopic topic() {
            return com.co.kc.imchat.model.enums.RedisTopic.PRIVATE_MESSAGE_SEND;
        }

        @Override
        public void onMessage(ImPrivateSentNotifyCmd message) {
        }
    }
}
