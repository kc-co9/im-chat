package com.co.kc.imchat.management.iam.model.io;

import java.util.Set;

/**
 * IAM 自身管理页面的当前认证主体响应。
 */
public record IamPrincipalResponse(
        Long administratorId,
        Set<String> authorities
) {
    public IamPrincipalResponse {
        authorities = Set.copyOf(authorities);
    }
}
