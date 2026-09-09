package com.co.kc.imchat.management.admin.model.cqrs.dto;

import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserStatus;

import java.time.Instant;

/**
 * 普通用户管理应用结果。
 */
public record ManagedUserDTO(Long id,
                             String username,
                             String email,
                             ManagedUserStatus status,
                             Boolean deleted,
                             Instant createdAt,
                             Instant updatedAt) {
}
