package com.co.kc.imchat.application.support.transaction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记方法在当前事务成功提交后执行。
 * <p>
 * 如果当前线程没有开启事务同步，方法会立即执行；如果存在事务同步，方法会被注册到
 * {@code afterCommit} 回调中，只有事务提交成功后才会执行。适用于发送通知、确认回执、
 * 取消重试任务等不应早于数据库提交完成的副作用操作。
 * <p>
 * 被标记的方法应只承载副作用逻辑，避免依赖返回值。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AfterTransactionCommit {
}
