package com.co.kc.imchat.application.support.notifier;

public interface ImMessageNotifier<T> {

    void notify(T command);
}
