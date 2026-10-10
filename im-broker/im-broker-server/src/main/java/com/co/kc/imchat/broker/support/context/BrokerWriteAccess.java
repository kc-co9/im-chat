package com.co.kc.imchat.broker.support.context;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记需要 Broker 管理写权限的 HTTP 入口。 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface BrokerWriteAccess {
    BrokerWriteAccessScope value();
    /** 只读检查无需提供幂等键，但仍校验权限和 epoch。 */
    boolean operationRequired() default true;
}
