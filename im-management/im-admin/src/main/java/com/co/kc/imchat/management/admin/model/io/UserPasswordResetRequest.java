package com.co.kc.imchat.management.admin.model.io;

import jakarta.validation.constraints.NotBlank;

public record UserPasswordResetRequest(Long userId, @NotBlank String password) {
}
