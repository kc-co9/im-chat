package com.co.kc.imchat.management.admin.model.io;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserUpdateRequest(Long userId, @NotBlank String username, @Email String email) {
}
