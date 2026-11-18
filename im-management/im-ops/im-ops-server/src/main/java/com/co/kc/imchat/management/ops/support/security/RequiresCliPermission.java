package com.co.kc.imchat.management.ops.support.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** CLI 用例同时要求可信 Token scope 和当前管理员权限。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAuthority('{value}') and authentication.details.scopes.contains('{value}')")
public @interface RequiresCliPermission {
    String value();
}
