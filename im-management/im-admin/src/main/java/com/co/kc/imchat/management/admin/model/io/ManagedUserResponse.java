package com.co.kc.imchat.management.admin.model.io;

import com.co.kc.imchat.management.admin.model.enums.ManagedUserStatusEnum;

import java.util.Objects;

public record ManagedUserResponse(Long id,
                                  String username,
                                  String email,
                                  ManagedUserStatusEnum status,
                                  Boolean deleted,
                                  Long createdAt,
                                  Long updatedAt) {
    public ManagedUserResponse {
        Objects.requireNonNull(deleted, "deleted must not be null");
    }
}
