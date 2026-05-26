package com.co.kc.imchat.application.model.cqrs.command.im;

import com.co.kc.imchat.application.support.notifier.task.ReceiptType;
import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * 客户端对 IM 消息通知的确认命令，用于停止通知重试，并按回执类型触发对应的 ACK 业务处理。
 */
public record ImMessageAckCmd(
        /* 发起通知确认的当前用户ID */
        Long userId,
        /* 确认通知对应的聊天ID */
        Long chatId,
        /* 确认通知对应的消息ID */
        Long messageId,
        /* 通知回执类型，用于区分发送、撤回等不同通知 */
        ReceiptType receiptType
) {
    public ImMessageAckCmd {
        AssertUtils.argNotNull("用户ID不能为空", userId);
        AssertUtils.argNotNull("聊天ID不能为空", chatId);
        AssertUtils.argNotNull("消息ID不能为空", messageId);
        AssertUtils.argNotNull("回执类型不能为空", receiptType);
    }
}
