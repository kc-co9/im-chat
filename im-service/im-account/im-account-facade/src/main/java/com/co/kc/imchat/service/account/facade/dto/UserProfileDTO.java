package com.co.kc.imchat.service.account.facade.dto;

import java.io.Serializable;

public record UserProfileDTO(Long userId, String username, String email) implements Serializable {
}
