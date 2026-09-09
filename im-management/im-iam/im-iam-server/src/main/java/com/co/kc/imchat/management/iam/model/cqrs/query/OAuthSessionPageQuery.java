package com.co.kc.imchat.management.iam.model.cqrs.query;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.utils.AssertUtils;

/** IAM OAuth 会话分页查询。 */
public record OAuthSessionPageQuery(Paging paging) {
    public OAuthSessionPageQuery {
        AssertUtils.argNotNull("paging must not be null", paging);
    }
}
