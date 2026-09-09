package com.co.kc.imchat.management.admin.model.cqrs.query;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.utils.AssertUtils;

public record ManagedUserPageQuery(Paging paging,
                                   Long userId,
                                   String username,
                                   String email,
                                   String status) {
    public ManagedUserPageQuery {
        username = username == null || username.isBlank() ? null : username.trim();
        email = email == null || email.isBlank() ? null : email.trim();
        status = status == null || status.isBlank() ? null : status.trim();
        AssertUtils.argNotNull("paging must not be null", paging);
    }
}
