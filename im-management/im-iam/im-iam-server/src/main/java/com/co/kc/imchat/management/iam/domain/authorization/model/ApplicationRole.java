package com.co.kc.imchat.management.iam.domain.authorization.model;

import com.co.kc.imchat.common.domain.shared.model.Identification;
import com.co.kc.imchat.common.exception.TransitionException;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;
import java.util.stream.Collectors;

/** IAM 应用范围内的角色聚合根。 */
@Getter
@EqualsAndHashCode(callSuper = false)
public class ApplicationRole extends Identification implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private ApplicationRoleId id;
    private AppId appId;
    private ApplicationRoleCode code;
    private ApplicationRoleName name;
    private ApplicationRoleType type;
    private ApplicationRoleStatus status;
    private Set<ApplicationPermissionId> permissionIds;

    private ApplicationRole() {
    }

    public static Builder builder() {
        return new Builder();
    }

    private void validate() {
        AssertUtils.allDomainPropNotNull(
                "role required properties must not be null",
                id, appId, code, name, type, status, permissionIds);
        permissionIds = Set.copyOf(permissionIds);
    }

    /** 创建并校验角色聚合。 */
    public static final class Builder {
        private final ApplicationRole role = new ApplicationRole();

        public Builder id(ApplicationRoleId id) {
            role.id = id;
            return this;
        }

        public Builder appId(AppId appId) {
            role.appId = appId;
            return this;
        }

        public Builder code(ApplicationRoleCode code) {
            role.code = code;
            return this;
        }

        public Builder name(ApplicationRoleName name) {
            role.name = name;
            return this;
        }

        public Builder type(ApplicationRoleType type) {
            role.type = type;
            return this;
        }

        public Builder status(ApplicationRoleStatus status) {
            role.status = status;
            return this;
        }

        public Builder permissionIds(Set<ApplicationPermissionId> permissionIds) {
            role.permissionIds = permissionIds;
            return this;
        }

        public ApplicationRole build() {
            role.validate();
            return role;
        }
    }

    public void changePermissions(Set<ApplicationPermission> permissions) {
        AssertUtils.domainPropNotNull("role permissions must not be null", permissions);
        boolean containsForeignPermission = permissions.stream()
                .anyMatch(permission -> !appId.equals(permission.getAppId()));
        if (containsForeignPermission) {
            throw new TransitionException("角色不能关联其他应用的权限");
        }
        permissionIds = permissions.stream()
                .map(ApplicationPermission::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 修订角色名称。 */
    public void reviseName(ApplicationRoleName name) {
        AssertUtils.domainPropNotNull("role name must not be null", name);
        this.name = name;
    }

    /** 判断当前角色是否处于启用状态。 */
    public boolean isActive() {
        return status == ApplicationRoleStatus.ACTIVE;
    }
}
