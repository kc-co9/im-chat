package com.co.kc.imchat.support.notifier;

import com.co.kc.imchat.support.notifier.task.NotifierTask;

public interface ImMessageConfirmableScheduler {

    void schedule(NotifierTask task);
}
