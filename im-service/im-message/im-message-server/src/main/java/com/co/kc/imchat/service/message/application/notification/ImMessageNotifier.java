package com.co.kc.imchat.service.message.application.notification;

/**
 * IM 消息通知器。
 *
 * @param <T> 通知类型
 */
public interface ImMessageNotifier<T> {

    /**
     * 发送通知。
     *
     * @param notification 通知内容
     */
    void notify(T notification);
}
