package com.co.kc.imchat.management.iam.model.cqrs.query;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.utils.AssertUtils;

/** IAM 管理员分页查询。 */
public record AdministratorPageQuery(Paging paging) {
    public AdministratorPageQuery {
        AssertUtils.argNotNull("paging must not be null", paging);
    }
}
