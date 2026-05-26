package com.co.kc.imchat.infrastructure.support.notifier;

import com.co.kc.imchat.application.model.notification.ImPrivateSentNotification;
import com.co.kc.imchat.application.model.enums.RedisTopic;
import com.co.kc.imchat.application.support.notifier.task.ReceiptType;
import com.co.kc.imchat.application.support.notifier.confirmable.ImMessageConfirmable;
import com.co.kc.imchat.application.support.redis.RedisPublisher;
import org.springframework.stereotype.Component;

@Component
public class PrivateSentNotifier extends AbstractRedisImMessageNotifier<ImPrivateSentNotification>
        implements ImMessageConfirmable<ImPrivateSentNotification> {

    public PrivateSentNotifier(RedisPublisher redisPublisher) {
        super(redisPublisher);
    }

    @Override
    protected RedisTopic topic() {
        return RedisTopic.PRIVATE_MESSAGE_SEND;
    }

    @Override
    public ReceiptType receiptType() {
        return ReceiptType.PRIVATE_MESSAGE_SEND;
    }

    @Override
    public String receiptId(ImPrivateSentNotification notification) {
        return receiptType().receiptId(notification.receiverId(), notification.chatId(), notification.messageId());
    }
}
