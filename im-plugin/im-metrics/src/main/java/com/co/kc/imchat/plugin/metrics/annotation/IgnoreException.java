package com.co.kc.imchat.plugin.metrics.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记失败后不应影响主流程的 best-effort 方法。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IgnoreException {
    /**
     * 是否记录被忽略的异常日志。
     *
     * @return 是否打印日志
     */
    boolean log() default true;
}
