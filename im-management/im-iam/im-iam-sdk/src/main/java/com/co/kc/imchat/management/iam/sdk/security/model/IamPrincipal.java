package com.co.kc.imchat.management.iam.sdk.security.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;
import java.util.Set;

/** 当前管理应用内的 IAM 认证主体。 */
public record IamPrincipal(
        Long administratorId,
        String username,
        String appKey,
        Set<String> authorities
) implements Serializable {
    public IamPrincipal {
        AssertUtils.allArgNotNull(
                "IAM principal required properties must not be null",
                administratorId, authorities);
        AssertUtils.argNotBlank("IAM principal username must not be blank", username);
        AssertUtils.argNotBlank("IAM principal appKey must not be blank", appKey);
        authorities = Set.copyOf(authorities);
    }
}
