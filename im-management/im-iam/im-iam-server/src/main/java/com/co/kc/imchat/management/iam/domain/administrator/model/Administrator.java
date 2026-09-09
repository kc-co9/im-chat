package com.co.kc.imchat.management.iam.domain.administrator.model;

import com.co.kc.imchat.common.domain.shared.model.Identification;
import com.co.kc.imchat.common.utils.AssertUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

/**
 * IAM 管理员聚合根。
 */
@Getter
@EqualsAndHashCode(callSuper = false)
public class Administrator extends Identification implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private AdministratorId id;
    private AdministratorUsername username;
    private AdministratorEmail email;
    private AdministratorPassword password;
    private AdministratorStatus status;

    private Administrator() {
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 判断管理员是否处于启用状态。
     */
    public boolean isActive() {
        return status == AdministratorStatus.ACTIVE;
    }

    public void disable() {
        status = AdministratorStatus.DISABLED;
    }

    public void enable() {
        status = AdministratorStatus.ACTIVE;
    }

    public void changePassword(AdministratorPassword newPassword) {
        AssertUtils.domainPropNotNull("new administrator password must not be null", newPassword);
        password = newPassword;
    }

    private void validate() {
        AssertUtils.allDomainPropNotNull(
                "administrator required properties must not be null",
                id, username, email, password, status);
    }

    /**
     * 创建并校验管理员聚合。
     */
    public static final class Builder {
        private final Administrator administrator = new Administrator();

        public Builder id(AdministratorId id) {
            administrator.id = id;
            return this;
        }

        public Builder username(AdministratorUsername username) {
            administrator.username = username;
            return this;
        }

        public Builder email(AdministratorEmail email) {
            administrator.email = email;
            return this;
        }

        public Builder password(AdministratorPassword password) {
            administrator.password = password;
            return this;
        }

        public Builder status(AdministratorStatus status) {
            administrator.status = status;
            return this;
        }

        public Administrator build() {
            administrator.validate();
            return administrator;
        }
    }

}
