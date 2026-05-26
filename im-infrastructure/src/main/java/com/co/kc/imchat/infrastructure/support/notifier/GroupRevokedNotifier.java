package com.co.kc.imchat.infrastructure.support.notifier;

import com.co.kc.imchat.application.model.notification.ImGroupRevokedNotification;
import com.co.kc.imchat.application.model.enums.RedisTopic;
import com.co.kc.imchat.application.support.notifier.confirmable.ImMessageConfirmable;
import com.co.kc.imchat.application.support.notifier.task.ReceiptType;
import com.co.kc.imchat.application.support.redis.RedisPublisher;
import org.springframework.stereotype.Component;

@Component
public class GroupRevokedNotifier extends AbstractRedisImMessageNotifier<ImGroupRevokedNotification>
        implements ImMessageConfirmable<ImGroupRevokedNotification> {

    public GroupRevokedNotifier(RedisPublisher redisPublisher) {
        super(redisPublisher);
    }

    @Override
    protected RedisTopic topic() {
        return RedisTopic.GROUP_MESSAGE_REVOKE;
    }

    @Override
    public ReceiptType receiptType() {
        return ReceiptType.GROUP_MESSAGE_REVOKE;
    }

    @Override
    public String receiptId(ImGroupRevokedNotification notification) {
        return receiptType().receiptId(notification.receiverId(), notification.chatId(), notification.messageId());
    }
}
