package com.co.kc.imchat.service.message.application.notification;

import com.co.kc.imchat.service.message.application.notification.confirmable.ImMessageConfirmable;
import com.co.kc.imchat.service.message.application.notification.confirmable.ImMessageConfirmableService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ImMessageNotifierInvoker {

    private final ImMessageNotifierFactory imMessageNotifierFactory;
    private final ImMessageConfirmableService imMessageConfirmableService;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> void invoke(T notification) {
        ImMessageNotifier<T> notifier = imMessageNotifierFactory.getNotifier(notification);
        if (notifier instanceof ImMessageConfirmable confirmable) {
            imMessageConfirmableService.schedule(confirmable, notification);
        }
        notifier.notify(notification);
    }
}
