package com.co.kc.imchat.plugin.metrics.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要记录调用次数、失败次数和执行耗时的 Spring Bean 公共方法。
 *
 * <p>被标记的方法需要通过 Spring 代理调用，直接创建对象并调用不会触发监控切面。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Observed {
    /**
     * 指标基础名称。
     *
     * @return 指标名称
     */
    String name();

    /**
     * Micrometer 标签键值对，必须按 key/value 成对传入。
     *
     * @return 标签键值数组
     */
    String[] tags() default {};

    /**
     * 是否忽略业务方法抛出的异常。
     *
     * <p>只适用于 best-effort 旁路动作；默认记录失败后继续抛出异常。</p>
     *
     * @return 是否忽略异常
     */
    boolean ignoreFailure() default false;
}
