package com.co.kc.imchat.infrastructure.support.lock.template;

@FunctionalInterface
public interface LockCallback<T> {
    T get() throws Throwable;
}
