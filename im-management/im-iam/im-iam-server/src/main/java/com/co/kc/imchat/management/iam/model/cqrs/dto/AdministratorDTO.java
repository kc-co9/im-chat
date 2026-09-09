package com.co.kc.imchat.management.iam.model.cqrs.dto;

import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorStatus;

/** IAM 管理账号查询结果。 */
public record AdministratorDTO(
        Long id,
        String username,
        String email,
        AdministratorStatus status
) {
}
