package com.co.kc.imchat.plugin.lock.annotation;

import com.co.kc.imchat.plugin.lock.support.LockConstants;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Inherited
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributeLock {
    String scene();

    String key();

    long expireTime() default LockConstants.AUTO_RENEW;

    long waitTime() default LockConstants.NEVER_WAIT;
}
