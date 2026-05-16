package com.co.kc.imchat.support.lock.template;

@FunctionalInterface
public interface LockCallback<T> {
    T get() throws Throwable;
}
