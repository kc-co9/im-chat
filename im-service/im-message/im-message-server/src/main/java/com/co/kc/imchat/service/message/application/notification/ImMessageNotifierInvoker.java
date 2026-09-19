package com.co.kc.imchat.service.message.application.notification;

import com.co.kc.imchat.service.message.application.notification.confirmable.ImMessageConfirmableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ImMessageNotifierInvoker {

    private final ImMessageNotifierFactory imMessageNotifierFactory;
    private final ImMessageConfirmableService imMessageConfirmableService;

    public <T> void invoke(T notification) {
        // 可确认通知先登记重试任务再异步投递，避免首次投递失败后失去补偿机会；无需重试的通知保留同步失败语义。
        ImMessageNotifier<T> notifier = imMessageNotifierFactory.getNotifier(notification);
        if (!imMessageConfirmableService.scheduleIfConfirmable(notifier, notification)) {
            notifier.notify(notification);
            return;
        }
        notifyAsync(notifier, notification);
    }

    private <T> void notifyAsync(ImMessageNotifier<T> notifier, T notification) {
        // 回执任务已持久化，虚拟线程启动或投递失败时由后续重试恢复。
        try {
            Thread.startVirtualThread(() -> {
                try {
                    notifier.notify(notification);
                } catch (RuntimeException exception) {
                    log.warn("initial message notification failed; scheduled retry remains active, notifier:{}, error:{}",
                            notifier.getClass().getSimpleName(), exception.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException exception) {
            log.warn("initial message notification was not submitted; scheduled retry remains active, notifier:{}, error:{}",
                    notifier.getClass().getSimpleName(), exception.getClass().getSimpleName());
        }
    }
}
