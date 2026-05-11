package com.co.kc.imchat.infrastructure.support;

import com.co.kc.imchat.support.notifier.ImMessageNotifierInvoker;
import com.co.kc.imchat.support.notifier.task.NotifierTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class ImMessageDelayConsumer implements Consumer<NotifierTask> {

    private final ImMessageNotifierInvoker imMessageNotifierInvoker;

    @Override
    public void accept(NotifierTask task) {
        imMessageNotifierInvoker.retry(task.getType(), task.getCommand());
    }
}
