package com.co.kc.imchat.support.notifier;

public interface ImMessageNotifier<T> {

    void notify(T command);
}
