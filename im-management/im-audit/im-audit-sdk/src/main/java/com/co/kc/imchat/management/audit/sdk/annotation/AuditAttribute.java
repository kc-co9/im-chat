package com.co.kc.imchat.management.audit.sdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明需要采集或排除的审计属性。 */
@Documented
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditAttribute {

    /** 是否包含在审计属性中。 */
    boolean include() default true;
}
