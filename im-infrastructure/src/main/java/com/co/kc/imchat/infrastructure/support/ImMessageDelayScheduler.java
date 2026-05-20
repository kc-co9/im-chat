package com.co.kc.imchat.infrastructure.support;

import com.co.kc.imchat.application.support.notifier.ImMessageConfirmableScheduler;
import com.co.kc.imchat.application.support.notifier.task.NotifierTask;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class ImMessageDelayScheduler implements ImMessageConfirmableScheduler {

    private final TaskScheduler taskScheduler;
    private final Consumer<NotifierTask> notifierTaskConsumer;

    @Override
    public void schedule(NotifierTask task) {
        taskScheduler.schedule(() -> notifierTaskConsumer.accept(task), Instant.ofEpochMilli(task.getNextAtMillis()));
    }
}
