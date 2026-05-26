package com.co.kc.imchat.application.support.notifier.confirmable;

import com.co.kc.imchat.application.support.notifier.task.ReceiptType;

public interface ImMessageConfirmable<T> {
    /**
     * 生成本次通知回执任务的唯一标识，用于确认和定位重试任务。
     */
    String receiptId(T notification);

    /**
     * 需要客户端回执的通知定义。
     */
    ReceiptType receiptType();

    /**
     * 等待多久后再次通知，毫秒。返回非正数表示不注册回执任务。
     */
    default long delayMillis() {
        return 2000L;
    }
}
