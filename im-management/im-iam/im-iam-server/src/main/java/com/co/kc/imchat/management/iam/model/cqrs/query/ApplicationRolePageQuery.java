package com.co.kc.imchat.management.iam.model.cqrs.query;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.utils.AssertUtils;

/** IAM 角色分页查询。 */
public record ApplicationRolePageQuery(Long appId, Paging paging) {
    public ApplicationRolePageQuery {
        AssertUtils.argNotNull("appId must not be null", appId);
        AssertUtils.argNotNull("paging must not be null", paging);
    }
}
