package com.co.kc.imchat.management.iam.domain.authorization.model;

import com.co.kc.imchat.common.domain.shared.model.Identification;
import com.co.kc.imchat.common.utils.AssertUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/** 保护 IAM 自身管理能力的内部角色聚合根。 */
@Getter
@EqualsAndHashCode(callSuper = false)
public class IamRole extends Identification implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final IamRoleId id;
    private final IamRoleCode code;
    private final IamRoleName name;
    private final IamRoleType type;
    private final IamRoleStatus status;
    private final Set<IamPermissionCode> permissions;

    public IamRole(
            IamRoleId id,
            IamRoleCode code,
            IamRoleName name,
            IamRoleType type,
            IamRoleStatus status,
            Set<IamPermissionCode> permissions
    ) {
        AssertUtils.allDomainPropNotNull(
                "iam role required properties must not be null",
                id, code, name, type, status, permissions);
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
        this.status = status;
        this.permissions = Set.copyOf(permissions);
    }

    public boolean isSuperAdmin() {
        return type == IamRoleType.SUPER_ADMIN;
    }

    public boolean isActive() {
        return status == IamRoleStatus.ACTIVE;
    }
}
