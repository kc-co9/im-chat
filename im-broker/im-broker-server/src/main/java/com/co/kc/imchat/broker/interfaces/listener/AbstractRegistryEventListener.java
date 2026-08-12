package com.co.kc.imchat.broker.interfaces.listener;

import lombok.extern.slf4j.Slf4j;

/**
 * 注册表事件监听器基类。
 * <p>
 * 统一包裹事件处理异常，避免单个同步事件失败影响 Spring 事件分发链路。
 */
@Slf4j
public abstract class AbstractRegistryEventListener {

    protected void handle(Runnable action, String eventName) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.warn("failed to handle registry sync event:{}, error:{}", eventName, ex.toString());
        }
    }
}
