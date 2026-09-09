package com.co.kc.imchat.management.iam.support.security;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.Set;

/**
 * IAM 自身管理请求中经过认证的管理员主体。
 */
public record IamAdministratorPrincipal(
        Long administratorId,
        Set<String> authorities
) {
    public IamAdministratorPrincipal {
        AssertUtils.allArgNotNull(
                "IAM administrator principal properties must not be null",
                administratorId,
                authorities);
        authorities = Set.copyOf(authorities);
    }
}
