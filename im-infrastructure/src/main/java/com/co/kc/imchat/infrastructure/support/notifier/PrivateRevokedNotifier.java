package com.co.kc.imchat.infrastructure.support.notifier;

import com.co.kc.imchat.application.model.notification.ImPrivateRevokedNotification;
import com.co.kc.imchat.application.model.enums.RedisTopic;
import com.co.kc.imchat.application.support.notifier.confirmable.ImMessageConfirmable;
import com.co.kc.imchat.application.support.notifier.task.ReceiptType;
import com.co.kc.imchat.application.support.redis.RedisPublisher;
import org.springframework.stereotype.Component;

@Component
public class PrivateRevokedNotifier extends AbstractRedisImMessageNotifier<ImPrivateRevokedNotification>
        implements ImMessageConfirmable<ImPrivateRevokedNotification> {

    public PrivateRevokedNotifier(RedisPublisher redisPublisher) {
        super(redisPublisher);
    }

    @Override
    protected RedisTopic topic() {
        return RedisTopic.PRIVATE_MESSAGE_REVOKE;
    }

    @Override
    public ReceiptType receiptType() {
        return ReceiptType.PRIVATE_MESSAGE_REVOKE;
    }

    @Override
    public String receiptId(ImPrivateRevokedNotification notification) {
        return receiptType().receiptId(notification.receiverId(), notification.receiverChatId(), notification.messageId());
    }
}
