package com.co.kc.imchat.service.account.model.cqrs.query;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.utils.AssertUtils;

public record ManagedUserPageQuery(
        Paging paging,
        Long userId,
        String username,
        String email,
        String status
) {
    public ManagedUserPageQuery {
        AssertUtils.argNotNull("paging must not be null", paging);
        if (userId != null) {
            AssertUtils.argTrue("userId must be positive", userId > 0L);
        }
        username = normalize(username);
        email = normalize(email);
        status = normalize(status);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
