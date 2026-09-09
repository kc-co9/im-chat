package com.co.kc.imchat.service.account.domain.user.model;

import com.co.kc.imchat.common.domain.shared.model.Identification;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import com.co.kc.imchat.common.exception.TransitionException;
import com.co.kc.imchat.common.utils.AssertUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

/**
 * 管理边界内的用户聚合，负责后台用户状态、资料、删除行为及写操作前置校验。
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class ManagedUser extends Identification {
    private final UserId userId;
    private UserName username;
    private UserEmail email;
    private UserPassword password;
    private UserStatus status;
    private Boolean deleted;
    private final Instant createdAt;
    private final Instant updatedAt;

    public ManagedUser(
            UserId userId,
            UserName username,
            UserEmail email,
            UserPassword password,
            UserStatus status,
            Boolean deleted,
            Instant createdAt,
            Instant updatedAt
    ) {
        AssertUtils.allDomainPropNotNull(
                "managed user properties must not be null",
                userId, username, email, password, status, deleted, createdAt, updatedAt);
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.password = password;
        this.status = status;
        this.deleted = deleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void ban() {
        ensureModifiable();
        status = UserStatus.BANNED;
    }

    public void unban() {
        ensureModifiable();
        if (!UserStatus.BANNED.equals(status)) {
            throw new TransitionException("只有已封禁用户可以解除封禁");
        }
        status = UserStatus.NORMAL;
    }

    public void changeUsername(UserName username) {
        AssertUtils.domainPropNotNull("username must not be null", username);
        ensureModifiable();
        if (!Objects.equals(this.username, username)) {
            this.username = username;
        }
    }

    public void changeEmail(UserEmail email) {
        AssertUtils.domainPropNotNull("email must not be null", email);
        ensureModifiable();
        if (!Objects.equals(this.email, email)) {
            this.email = email;
        }
    }

    public void changePassword(UserPassword password) {
        AssertUtils.domainPropNotNull("password must not be null", password);
        ensureModifiable();
        if (!Objects.equals(this.password, password)) {
            this.password = password;
        }
    }

    /**
     * 校验当前用户允许执行删除操作。
     */
    public void ensureDeletable() {
        if (Boolean.TRUE.equals(deleted)) {
            throw new TransitionException("用户已删除");
        }
    }

    /**
     * 校验当前用户允许执行管理写操作。
     */
    public void ensureModifiable() {
        if (Boolean.TRUE.equals(deleted)) {
            throw new TransitionException("已删除用户不允许修改");
        }
    }
}
