package com.co.kc.imchat.application.support.notifier.confirmable;

import com.co.kc.imchat.application.support.notifier.ImMessageNotifierFactory;
import com.co.kc.imchat.application.support.notifier.task.ReceiptTask;
import com.co.kc.imchat.common.utils.JsonUtils;

public class ImMessageConfirmableService {
    private final ImMessageNotifierFactory imMessageNotifierFactory;
    private final ImMessageConfirmableStore imMessageConfirmableStore;

    public ImMessageConfirmableService(ImMessageNotifierFactory imMessageNotifierFactory,
                                       ImMessageConfirmableStore imMessageConfirmableStore) {
        this.imMessageNotifierFactory = imMessageNotifierFactory;
        this.imMessageConfirmableStore = imMessageConfirmableStore;
    }

    public void init() {
        imMessageConfirmableStore.startConfirming(this::executeTask);
    }

    public void destroy() {
        imMessageConfirmableStore.stopConfirming();
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

    private void executeTask(ReceiptTask task) {
        Class<?> notificationType = imMessageNotifierFactory.getNotificationType(task.getReceiptType());
        Object notification = JsonUtils.getMapper().convertValue(task.getNotification(), notificationType);
        imMessageNotifierFactory.getNotifier(task.getReceiptType()).notify(notification);
    }

    public void confirm(String receiptId) {
        imMessageConfirmableStore.confirm(receiptId);
    }
}
