package com.co.kc.imchat.management.iam.model.io;

import com.co.kc.imchat.management.iam.model.enums.IamAdministratorStatusEnum;

/** IAM 管理账号 HTTP 响应。 */
public record IamAdministratorResponse(
        Long id,
        String username,
        String email,
        IamAdministratorStatusEnum status
) {
}
