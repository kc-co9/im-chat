package com.co.kc.imchat.management.audit.sdk.annotation;

import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明应用用例完成后需要产生一个审计事件。
 *
 * <p>注解只描述审计语义，不承载权限规则。动态属性由方法参数上的
 * {@link AuditAttribute} 和返回值共同提供。</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** 审计事件类别。 */
    AuditType type();

    /** 来源应用定义的稳定动作码。 */
    String action();

    /** 操作目标类型。 */
    String targetType();

    /** 解析目标标识的 SpEL，可为空。 */
    String targetId() default "";

    /** 已脱敏的固定审计说明。 */
    String description();
}
