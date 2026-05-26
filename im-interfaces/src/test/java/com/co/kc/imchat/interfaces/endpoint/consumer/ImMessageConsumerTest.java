package com.co.kc.imchat.interfaces.endpoint.consumer;

import com.co.kc.imchat.application.model.notification.ImGroupRevokedNotification;
import com.co.kc.imchat.application.model.notification.ImGroupSentNotification;
import com.co.kc.imchat.application.model.notification.ImPrivateRevokedNotification;
import com.co.kc.imchat.application.model.notification.ImPrivateSentNotification;
import com.co.kc.imchat.application.model.enums.RedisTopic;
import com.co.kc.imchat.domain.message.model.ImMessageTypeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;

import static com.co.kc.imchat.interfaces.support.websocket.PushQueue.QUEUE_GROUP_MESSAGE_REVOKED;
import static com.co.kc.imchat.interfaces.support.websocket.PushQueue.QUEUE_GROUP_MESSAGE_SENT;
import static com.co.kc.imchat.interfaces.support.websocket.PushQueue.QUEUE_PRIVATE_MESSAGE_REVOKED;
import static com.co.kc.imchat.interfaces.support.websocket.PushQueue.QUEUE_PRIVATE_MESSAGE_SENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ImMessageConsumerTest {

    @Test
    void privateMessageSentConsumerPushesRedisMessageToReceiverUserQueue() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ImPrivateMessageSentConsumer consumer = new ImPrivateMessageSentConsumer(messagingTemplate);
        ImPrivateSentNotification notification = new ImPrivateSentNotification(
                900L, 102L, 1L, 2L, ImMessageTypeEnum.TEXT, "hello", LocalDateTime.now());

        consumer.onMessage(notification);

        assertThat(consumer.topic()).isEqualTo(RedisTopic.PRIVATE_MESSAGE_SEND);
        verify(messagingTemplate).convertAndSendToUser("2", QUEUE_PRIVATE_MESSAGE_SENT, notification);
    }

    @Test
    void privateMessageRevokedConsumerPushesRedisMessageToReceiverUserQueue() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ImPrivateMessageRevokedConsumer consumer = new ImPrivateMessageRevokedConsumer(messagingTemplate);
        ImPrivateRevokedNotification notification = new ImPrivateRevokedNotification(2L, 102L, 900L);

        consumer.onMessage(notification);

        assertThat(consumer.topic()).isEqualTo(RedisTopic.PRIVATE_MESSAGE_REVOKE);
        verify(messagingTemplate).convertAndSendToUser("2", QUEUE_PRIVATE_MESSAGE_REVOKED, notification);
    }

    @Test
    void groupMessageSentConsumerPushesRedisMessageToReceiverUserQueue() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ImGroupMessageSentConsumer consumer = new ImGroupMessageSentConsumer(messagingTemplate);
        ImGroupSentNotification notification = new ImGroupSentNotification(
                900L, 102L, 1L, 2L, ImMessageTypeEnum.TEXT, "hello", LocalDateTime.now());

        consumer.onMessage(notification);

        assertThat(consumer.topic()).isEqualTo(RedisTopic.GROUP_MESSAGE_SEND);
        verify(messagingTemplate).convertAndSendToUser("2", QUEUE_GROUP_MESSAGE_SENT, notification);
    }

    @Test
    void groupMessageRevokedConsumerPushesRedisMessageToReceiverUserQueue() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ImGroupMessageRevokedConsumer consumer = new ImGroupMessageRevokedConsumer(messagingTemplate);
        ImGroupRevokedNotification notification = new ImGroupRevokedNotification(102L, 900L, 2L);

        consumer.onMessage(notification);

        assertThat(consumer.topic()).isEqualTo(RedisTopic.GROUP_MESSAGE_REVOKE);
        verify(messagingTemplate).convertAndSendToUser("2", QUEUE_GROUP_MESSAGE_REVOKED, notification);
    }
}
