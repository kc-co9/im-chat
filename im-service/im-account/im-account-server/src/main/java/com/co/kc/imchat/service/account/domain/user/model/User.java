package com.co.kc.imchat.service.account.domain.user.model;

import com.co.kc.imchat.common.domain.shared.model.Identification;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.domain.user.model.UserName;
import com.co.kc.imchat.common.exception.AuthException;
import com.co.kc.imchat.service.account.domain.user.service.PasswordService;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 聚合根：用户。
 */
@Getter
@EqualsAndHashCode(callSuper = false)
public class User extends Identification implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 聚合根-唯一标识
     */
    private UserId id;
    private UserEmail email;
    private UserName username;
    private UserPassword password;
    private UserStatus status;

    public User(UserId id,
                UserEmail email,
                UserName username,
                UserPassword password,
                UserStatus status) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.password = password;
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public void changeEmail(UserEmail email) {
        if (!Objects.equals(this.email, email)) {
            this.email = email;
        }
    }

    public void changeUserName(UserName username) {
        if (!Objects.equals(this.username, username)) {
            this.username = username;
        }
    }

    /**
     * 校验当前密码后更新用户密码。
     *
     * @param oldPassword 当前密码
     * @param newPassword 新密码
     * @param passwordService 密码加密与校验服务
     */
    public void changePassword(
            UserRawPassword oldPassword,
            UserRawPassword newPassword,
            PasswordService passwordService
    ) {
        if (!validateRawPassword(oldPassword, passwordService)) {
            throw new AuthException("用户认证失败");
        }
        this.password = passwordService.encrypt(newPassword);
    }

    public boolean validateRawPassword(UserRawPassword rawPassword, PasswordService passwordService) {
        return passwordService.verify(rawPassword, this.password);
    }

    public boolean canAuthenticate() {
        return status == UserStatus.NORMAL;
    }


}
