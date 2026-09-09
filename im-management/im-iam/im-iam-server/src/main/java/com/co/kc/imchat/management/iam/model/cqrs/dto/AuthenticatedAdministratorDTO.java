package com.co.kc.imchat.management.iam.model.cqrs.dto;

import java.util.Set;

/** 已通过凭据校验的管理员身份。 */
public record AuthenticatedAdministratorDTO(
        Long administratorId,
        String username,
        String email,
        Set<String> permissions
) {
    public AuthenticatedAdministratorDTO {
        permissions = Set.copyOf(permissions);
    }

}
