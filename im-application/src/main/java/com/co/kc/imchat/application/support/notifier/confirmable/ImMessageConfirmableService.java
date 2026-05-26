package com.co.kc.imchat.application.support.notifier.confirmable;

import com.co.kc.imchat.application.support.notifier.ImMessageNotifierFactory;
import com.co.kc.imchat.application.support.notifier.task.ReceiptTask;
import com.co.kc.imchat.common.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ImMessageConfirmableService {

    private final ImMessageNotifierFactory imMessageNotifierFactory;
    private final ImMessageConfirmableStore imMessageConfirmableStore;
    private final ScheduledExecutorService scheduledExecutorService;

    public ImMessageConfirmableService(ImMessageNotifierFactory imMessageNotifierFactory,
                                       ImMessageConfirmableStore imMessageConfirmableStore,
                                       ScheduledExecutorService scheduledExecutorService) {
        this.imMessageNotifierFactory = imMessageNotifierFactory;
        this.imMessageConfirmableStore = imMessageConfirmableStore;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    public void init() {
        scheduledExecutorService.scheduleWithFixedDelay(this::consumeSafely, 1, 1, TimeUnit.SECONDS);
    }

    public void destroy() {
        scheduledExecutorService.shutdown();
    }

    public <T> void schedule(ImMessageConfirmable<T> confirmable, T notification) {
        if (confirmable.delayMillis() <= 0) {
            return;
        }
        ReceiptTask task = ReceiptTask.builder()
                .receiptType(confirmable.receiptType())
                .receiptId(confirmable.receiptId(notification))
                .notification(notification)
                .delayMillis(confirmable.delayMillis())
                .build();
        imMessageConfirmableStore.offer(task);
    }

    private void consumeSafely() {
        try {
            imMessageConfirmableStore.consume(this::executeTask);
        } catch (Exception e) {
            log.error("消费消息确认重试任务失败", e);
        }
    }

    private void executeTask(ReceiptTask task) {
        Class<?> notificationType = imMessageNotifierFactory.getNotificationType(task.getReceiptType());
        Object notification = JsonUtils.getMapper().convertValue(task.getNotification(), notificationType);
        imMessageNotifierFactory.getNotifier(task.getReceiptType()).notify(notification);
    }

    public <T> void confirm(String receiptId) {
        imMessageConfirmableStore.confirm(receiptId);
    }
}
