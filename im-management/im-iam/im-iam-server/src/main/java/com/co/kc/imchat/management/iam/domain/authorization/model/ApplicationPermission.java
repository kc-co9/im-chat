package com.co.kc.imchat.management.iam.domain.authorization.model;

import com.co.kc.imchat.common.exception.TransitionException;
import com.co.kc.imchat.common.domain.shared.model.Identification;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

/** 应用权限目录项。 */
@Getter
@EqualsAndHashCode(callSuper = false)
public class ApplicationPermission extends Identification implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private ApplicationPermissionId id;
    private AppId appId;
    private ApplicationPermissionCode code;
    private ApplicationPermissionName name;
    private ApplicationPermissionDescription description;
    private ApplicationPermissionStatus status;

    private ApplicationPermission() {
    }

    public static Builder builder() {
        return new Builder();
    }

    private void validate() {
        AssertUtils.allDomainPropNotNull(
                "permission required properties must not be null",
                id, appId, code, name, description, status);
    }

    /** 创建并校验权限聚合。 */
    public static final class Builder {
        private final ApplicationPermission permission = new ApplicationPermission();

        public Builder id(ApplicationPermissionId id) {
            permission.id = id;
            return this;
        }

        public Builder appId(AppId appId) {
            permission.appId = appId;
            return this;
        }

        public Builder code(ApplicationPermissionCode code) {
            permission.code = code;
            return this;
        }

        public Builder name(ApplicationPermissionName name) {
            permission.name = name;
            return this;
        }

        public Builder description(ApplicationPermissionDescription description) {
            permission.description = description;
            return this;
        }

        public Builder status(ApplicationPermissionStatus status) {
            permission.status = status;
            return this;
        }

        public ApplicationPermission build() {
            permission.validate();
            return permission;
        }
    }

    public void revise(
            ApplicationPermissionName newName,
            ApplicationPermissionDescription newDescription
    ) {
        AssertUtils.allDomainPropNotNull(
                "permission synchronization properties must not be null",
                newName, newDescription);
        name = newName;
        description = newDescription;
        status = ApplicationPermissionStatus.ACTIVE;
    }

    public void deactivate() {
        status = ApplicationPermissionStatus.INACTIVE;
    }

    public void ensureRemovable() {
        if (status != ApplicationPermissionStatus.INACTIVE) {
            throw new TransitionException("只有已停用权限可以删除");
        }
    }
}
