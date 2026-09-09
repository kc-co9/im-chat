package com.co.kc.imchat.service.account.model.cqrs.dto;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.service.account.domain.user.model.UserStatus;

import java.time.Instant;

public record ManagedUserListDTO(
        Long userId,
        String username,
        String email,
        UserStatus status,
        Boolean deleted,
        Instant createdAt,
        Instant updatedAt
) {
    public ManagedUserListDTO {
        AssertUtils.argNotNull("deleted must not be null", deleted);
    }
}
