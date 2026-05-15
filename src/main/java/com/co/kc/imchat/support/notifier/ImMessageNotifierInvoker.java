package com.co.kc.imchat.support.notifier;

import com.co.kc.imchat.support.notifier.task.NotifierTask;
import com.co.kc.imchat.support.notifier.task.NotifierTaskType;
import com.co.kc.imchat.support.utils.JsonUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class ImMessageNotifierInvoker {

    private final ImMessageNotifierFactory imMessageNotifierFactory;
    private final ImMessageConfirmableScheduler imMessageConfirmableScheduler;

    public ImMessageNotifierInvoker(ImMessageNotifierFactory imMessageNotifierFactory,
                                    @Lazy ImMessageConfirmableScheduler imMessageConfirmableScheduler) {
        this.imMessageNotifierFactory = imMessageNotifierFactory;
        this.imMessageConfirmableScheduler = imMessageConfirmableScheduler;
    }

    public <T> void invoke(T command) {
        ImMessageNotifier<T> notifier = imMessageNotifierFactory.getNotifier(command);
        scheduleIfNecessary(notifier, command);
        notifier.notify(command);
    }

    public <T> void retry(NotifierTaskType type, T payload) {
        T command = deserializeIfNecessary(type, payload);
        imMessageNotifierFactory.<T>getNotifier(type).notify(command);
    }

    private <T> void scheduleIfNecessary(ImMessageNotifier<T> notifier, T command) {
        if (!(notifier instanceof ImMessageConfirmable)) {
            return;
        }
        ImMessageConfirmable confirmable = (ImMessageConfirmable) notifier;
        if (confirmable.delay() <= 0) {
            return;
        }
        imMessageConfirmableScheduler.schedule(buildTask(confirmable, command));
    }

    private NotifierTask buildTask(ImMessageConfirmable confirmable, Object command) {
        long now = System.currentTimeMillis();
        NotifierTask task = new NotifierTask();
        task.setType(confirmable.task());
        task.setCommand(JsonUtils.toJson(command));
        task.setAttempts(0);
        task.setCreatedAtMillis(now);
        task.setNextAtMillis(now + confirmable.delay());
        return task;
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeIfNecessary(NotifierTaskType type, T payload) {
        if (!(payload instanceof String)) {
            return payload;
        }
        Class<?> commandType = imMessageNotifierFactory.getCommandType(type);
        if (String.class.equals(commandType)) {
            return payload;
        }
        return (T) JsonUtils.fromJson((String) payload, commandType);
    }
}
