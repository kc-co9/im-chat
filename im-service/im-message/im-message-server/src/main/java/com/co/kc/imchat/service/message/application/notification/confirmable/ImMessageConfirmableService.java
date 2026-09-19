package com.co.kc.imchat.service.message.application.notification.confirmable;

import com.co.kc.imchat.service.message.application.notification.ImMessageNotifier;
import com.co.kc.imchat.service.message.application.notification.ImMessageNotifierFactory;
import com.co.kc.imchat.service.message.application.notification.task.ReceiptTask;
import com.co.kc.imchat.common.utils.JsonUtils;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ImMessageConfirmableService {
    private final ImMessageNotifierFactory imMessageNotifierFactory;
    private final ImMessageConfirmableStore imMessageConfirmableStore;

    public void init() {
        imMessageConfirmableStore.startConfirming(this::executeTask);
    }

    public void destroy() {
        imMessageConfirmableStore.stopConfirming();
    }

    @SuppressWarnings("unchecked")
    public <T> boolean scheduleIfConfirmable(ImMessageNotifier<T> notifier, T notification) {
        if (!(notifier instanceof ImMessageConfirmable<?> confirmable)
                || confirmable.delayMillis() <= 0) {
            return false;
        }
        ImMessageConfirmable<T> messageConfirmable = (ImMessageConfirmable<T>) confirmable;
        ReceiptTask task = ReceiptTask.builder()
                .receiptType(messageConfirmable.receiptType())
                .receiptId(messageConfirmable.receiptId(notification))
                .notification(notification)
                .delayMillis(messageConfirmable.delayMillis())
                .build();
        imMessageConfirmableStore.offer(task);
        return true;
    }

    private void executeTask(ReceiptTask task) {
        Class<?> notificationType = imMessageNotifierFactory.getNotificationType(task.getReceiptType());
        Object notification = JsonUtils.convertValue(task.getNotification(), notificationType);
        imMessageNotifierFactory.getNotifier(task.getReceiptType()).notify(notification);
    }

    public void confirm(String receiptId) {
        imMessageConfirmableStore.confirm(receiptId);
    }
}
