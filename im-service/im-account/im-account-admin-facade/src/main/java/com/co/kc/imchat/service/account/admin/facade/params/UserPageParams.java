package com.co.kc.imchat.service.account.admin.facade.params;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.service.account.admin.facade.enums.AccountUserStatus;

import java.io.Serial;
import java.io.Serializable;

/** 普通用户分页查询参数。 */
public record UserPageParams(
        Paging paging,
        Long userId,
        String username,
        String email,
        AccountUserStatus status
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public UserPageParams {
        AssertUtils.argNotNull("paging must not be null", paging);
        AssertUtils.argTrue("pageSize must not exceed 100", paging.pageSize() <= 100);
        AssertUtils.argTrue("userId must be positive when present",
                userId == null || userId > 0);
        username = normalize(username);
        email = normalize(email);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
