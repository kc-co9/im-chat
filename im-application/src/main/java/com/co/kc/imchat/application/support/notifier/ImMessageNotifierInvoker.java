package com.co.kc.imchat.application.support.notifier;

import com.co.kc.imchat.application.support.notifier.confirmable.ImMessageConfirmable;
import com.co.kc.imchat.application.support.notifier.confirmable.ImMessageConfirmableService;

public class ImMessageNotifierInvoker {

    private final ImMessageNotifierFactory imMessageNotifierFactory;
    private final ImMessageConfirmableService imMessageConfirmableService;

    public ImMessageNotifierInvoker(ImMessageNotifierFactory imMessageNotifierFactory,
                                    ImMessageConfirmableService imMessageConfirmableService) {
        this.imMessageNotifierFactory = imMessageNotifierFactory;
        this.imMessageConfirmableService = imMessageConfirmableService;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> void invoke(T notification) {
        ImMessageNotifier<T> notifier = imMessageNotifierFactory.getNotifier(notification);
        if (notifier instanceof ImMessageConfirmable confirmable) {
            imMessageConfirmableService.schedule(confirmable, notification);
        }
        notifier.notify(notification);
    }
}
