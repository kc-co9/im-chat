package com.co.kc.imchat.application.support.notifier;

import com.co.kc.imchat.application.support.notifier.task.NotifierTaskType;

public interface ImMessageConfirmable {

    /**
     * 确认任务定义，包含确认 key 生成规则与任务执行入口编码。
     */
    NotifierTaskType task();

    /**
     * 确认超时时间，毫秒。返回负数表示不注册确认任务。
     */
    default long delay() {
        return 2000L;
    }
}
