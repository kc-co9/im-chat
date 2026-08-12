package com.co.kc.imchat.service.message.facade;

import com.co.kc.imchat.service.message.facade.params.GroupMessageReadParams;
import com.co.kc.imchat.service.message.facade.params.GroupMessageRevokeParams;
import com.co.kc.imchat.service.message.facade.params.GroupMessageSendParams;
import com.co.kc.imchat.service.message.facade.params.NotificationAckParams;
import com.co.kc.imchat.service.message.facade.params.PrivateMessageReadParams;
import com.co.kc.imchat.service.message.facade.params.PrivateMessageRevokeParams;
import com.co.kc.imchat.service.message.facade.params.PrivateMessageSendParams;

/**
 * 消息服务。
 * <p>
 * Broker 调用该接口完成客户端 WS 上行消息命令对应的业务处理。
 */
public interface MessageService {
    /**
     * 发送单聊消息。
     *
     * @param params 单聊消息发送请求
     */
    void sendPrivateMessage(PrivateMessageSendParams params);

    /**
     * 标记单聊消息已读。
     *
     * @param params 单聊消息已读请求
     */
    void readPrivateMessage(PrivateMessageReadParams params);

    /**
     * 撤回单聊消息。
     *
     * @param params 单聊消息撤回请求
     */
    void revokePrivateMessage(PrivateMessageRevokeParams params);

    /**
     * 发送群聊消息。
     *
     * @param params 群聊消息发送请求
     */
    void sendGroupMessage(GroupMessageSendParams params);

    /**
     * 标记群聊消息已读。
     *
     * @param params 群聊消息已读请求
     */
    void readGroupMessage(GroupMessageReadParams params);

    /**
     * 撤回群聊消息。
     *
     * @param params 群聊消息撤回请求
     */
    void revokeGroupMessage(GroupMessageRevokeParams params);

    /**
     * 确认通知类消息已送达或已处理。
     *
     * @param params 通知确认请求
     */
    void ackNotification(NotificationAckParams params);
}
