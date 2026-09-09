package com.co.kc.imchat.management.iam.model.cqrs.query;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.utils.AssertUtils;

/** IAM 应用分页查询。 */
public record ApplicationPageQuery(Paging paging) {
    public ApplicationPageQuery {
        AssertUtils.argNotNull("paging must not be null", paging);
    }
}
