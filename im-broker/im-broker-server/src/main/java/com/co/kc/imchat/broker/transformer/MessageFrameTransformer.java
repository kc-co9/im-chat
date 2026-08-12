package com.co.kc.imchat.broker.transformer;

import com.co.kc.imchat.common.model.io.FrameRequest;
import com.co.kc.imchat.common.utils.JsonUtils;
import com.co.kc.imchat.service.message.facade.params.GroupMessageReadParams;
import com.co.kc.imchat.service.message.facade.params.GroupMessageRevokeParams;
import com.co.kc.imchat.service.message.facade.params.GroupMessageSendParams;
import com.co.kc.imchat.service.message.facade.params.NotificationAckParams;
import com.co.kc.imchat.service.message.facade.params.PrivateMessageReadParams;
import com.co.kc.imchat.service.message.facade.params.PrivateMessageRevokeParams;
import com.co.kc.imchat.service.message.facade.params.PrivateMessageSendParams;

/**
 * 消息实时帧转换器。
 * <p>
 * Broker 使用连接态用户 ID 覆盖帧 body 中的 userId，避免客户端伪造发送人。
 */
public final class MessageFrameTransformer {
    public static final MessageFrameTransformer INSTANCE = new MessageFrameTransformer();

    private MessageFrameTransformer() {
    }

    public PrivateMessageSendParams privateMessageSendParamsFrom(Long userId, FrameRequest frame) {
        PrivateMessageSendParams params = body(frame, PrivateMessageSendParams.class);
        return new PrivateMessageSendParams(requireUserId(userId), params.chatId(), params.messageToken(),
                params.messageType(), params.messageContent());
    }

    public PrivateMessageReadParams privateMessageReadParamsFrom(Long userId, FrameRequest frame) {
        PrivateMessageReadParams params = body(frame, PrivateMessageReadParams.class);
        return new PrivateMessageReadParams(requireUserId(userId), params.chatId(), params.messageId());
    }

    public PrivateMessageRevokeParams privateMessageRevokeParamsFrom(Long userId, FrameRequest frame) {
        PrivateMessageRevokeParams params = body(frame, PrivateMessageRevokeParams.class);
        return new PrivateMessageRevokeParams(requireUserId(userId), params.chatId(), params.messageId());
    }

    public GroupMessageSendParams groupMessageSendParamsFrom(Long userId, FrameRequest frame) {
        GroupMessageSendParams params = body(frame, GroupMessageSendParams.class);
        return new GroupMessageSendParams(requireUserId(userId), params.chatId(), params.messageToken(),
                params.messageType(), params.messageContent());
    }

    public GroupMessageReadParams groupMessageReadParamsFrom(Long userId, FrameRequest frame) {
        GroupMessageReadParams params = body(frame, GroupMessageReadParams.class);
        return new GroupMessageReadParams(requireUserId(userId), params.chatId(), params.messageId());
    }

    public GroupMessageRevokeParams groupMessageRevokeParamsFrom(Long userId, FrameRequest frame) {
        GroupMessageRevokeParams params = body(frame, GroupMessageRevokeParams.class);
        return new GroupMessageRevokeParams(requireUserId(userId), params.chatId(), params.messageId());
    }

    public NotificationAckParams notificationAckParamsFrom(Long userId, FrameRequest frame) {
        NotificationAckParams params = body(frame, NotificationAckParams.class);
        return new NotificationAckParams(requireUserId(userId), params.chatId(), params.messageId(),
                params.receiptType());
    }

    private <T> T body(FrameRequest frame, Class<T> paramsType) {
        if (frame.body() == null) {
            throw new IllegalArgumentException("请求 body 不能为空");
        }
        return JsonUtils.convertValue(frame.body(), paramsType);
    }

    private Long requireUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("连接用户不能为空");
        }
        return userId;
    }
}
