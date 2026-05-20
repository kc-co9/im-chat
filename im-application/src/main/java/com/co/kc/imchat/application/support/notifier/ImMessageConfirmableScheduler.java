package com.co.kc.imchat.application.support.notifier;

import com.co.kc.imchat.application.support.notifier.task.NotifierTask;

public interface ImMessageConfirmableScheduler {

    void schedule(NotifierTask task);
}
