package com.co.kc.imchat.service.message.application.notification.confirmable;

import com.co.kc.imchat.service.message.application.notification.task.ReceiptTask;
import java.util.function.Consumer;

/**
 * 保存需要客户端回执的通知重投任务。
 *
 * <p>任务按 {@code receiptId} 定位。客户端确认后删除任务；
 * 后续队列里残留的 {@code receiptId} 如果找不到任务内容，会被直接跳过。</p>
 */
public interface ImMessageConfirmableStore {

    /**
     * 启动通知回执重投任务消费。
     *
     * <p>consumer 正常结束后，实现方确认当前任务并按需注册下一轮延迟任务；
     * consumer 抛异常时，实现方应尽量保留后续重投机会。</p>
     */
    void startConfirming(Consumer<ReceiptTask> consumer);

    /**
     * 停止通知回执重投任务消费。
     */
    void stopConfirming();

    /**
     * 将需确认的消息放入延迟重试队列。
     */
    void offer(ReceiptTask message);


    /**
     * 确认回执并删除对应的重投任务。
     */
    void confirm(String receiptId);

}
