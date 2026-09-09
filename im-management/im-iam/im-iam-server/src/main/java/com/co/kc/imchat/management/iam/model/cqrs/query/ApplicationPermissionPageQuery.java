package com.co.kc.imchat.management.iam.model.cqrs.query;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.utils.AssertUtils;

/** 查询指定应用的权限目录。 */
public record ApplicationPermissionPageQuery(Long appId, String keyword, Paging paging) {
    public ApplicationPermissionPageQuery {
        AssertUtils.argNotNull("appId must not be null", appId);
        AssertUtils.argNotNull("paging must not be null", paging);
        keyword = normalize(keyword);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
