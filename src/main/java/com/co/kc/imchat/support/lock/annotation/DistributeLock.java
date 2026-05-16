package com.co.kc.imchat.support.lock.annotation;

import com.co.kc.imchat.support.lock.DistributeLockScene;
import com.co.kc.imchat.support.lock.constant.LockConstant;

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
    DistributeLockScene scene();

    String key();

    long expireTime() default LockConstant.AUTO_RENEW;

    long waitTime() default LockConstant.NEVER_WAIT;
}
