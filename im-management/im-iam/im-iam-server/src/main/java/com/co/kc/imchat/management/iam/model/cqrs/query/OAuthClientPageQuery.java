package com.co.kc.imchat.management.iam.model.cqrs.query;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.utils.AssertUtils;

/** IAM OAuth 客户端分页查询。 */
public record OAuthClientPageQuery(Long appId, Paging paging) {
    public OAuthClientPageQuery {
        AssertUtils.allArgNotNull(
                "oauth client page query must not contain null values",
                appId,
                paging);
    }
}
