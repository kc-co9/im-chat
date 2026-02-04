package com.co.kc.imchat.domain.user;

import com.co.kc.imchat.domain.shared.Identification;
import com.co.kc.imchat.support.user.PasswordService;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;

/**
 * 用户领域
 */
@Getter
@EqualsAndHashCode(callSuper = false)
public class User extends Identification {
    /**
     * 聚合根-唯一标识
     */
    private UserId id;
    private UserEmail email;
    private UserName username;
    private UserPassword password;

    public User(UserId id, UserEmail email, UserName username, UserPassword password) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.password = password;
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

    public void changePassword(UserPassword password) {
        if (!Objects.equals(this.password, password)) {
            this.password = password;
        }
    }

    public boolean validateRawPassword(UserRawPassword rawPassword, PasswordService passwordService) {
        return passwordService.verify(rawPassword, this.password);
    }


}
