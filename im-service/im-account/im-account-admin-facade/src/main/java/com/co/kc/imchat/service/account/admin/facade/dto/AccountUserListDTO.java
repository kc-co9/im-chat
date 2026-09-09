package com.co.kc.imchat.service.account.admin.facade.dto;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.service.account.admin.facade.enums.AccountUserStatus;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * 管理端普通用户列表项。
 */
public record AccountUserListDTO(
        Long userId,
        String username,
        String email,
        AccountUserStatus status,
        Boolean deleted,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public AccountUserListDTO {
        AssertUtils.argTrue("userId must be positive", userId != null && userId > 0);
        AssertUtils.argNotBlank("username must not be blank", username);
        AssertUtils.argNotBlank("email must not be blank", email);
        AssertUtils.argNotNull("status must not be null", status);
        AssertUtils.argNotNull("deleted must not be null", deleted);
        AssertUtils.argNotNull("createdAt must not be null", createdAt);
        AssertUtils.argNotNull("updatedAt must not be null", updatedAt);
        username = username.trim();
        email = email.trim();
    }
}
