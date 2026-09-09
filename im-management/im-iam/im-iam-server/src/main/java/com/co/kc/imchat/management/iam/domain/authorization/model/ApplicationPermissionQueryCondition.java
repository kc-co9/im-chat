package com.co.kc.imchat.management.iam.domain.authorization.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.Optional;

/** 应用权限目录查询条件。 */
public record ApplicationPermissionQueryCondition(Optional<String> keyword) {
    public ApplicationPermissionQueryCondition {
        AssertUtils.domainPropNotNull(
                "application permission query keyword option must not be null",
                keyword);
        keyword = keyword
                .map(String::trim)
                .filter(value -> !value.isBlank());
    }
}
