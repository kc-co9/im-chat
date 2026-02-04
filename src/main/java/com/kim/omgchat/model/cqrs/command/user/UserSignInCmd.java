package com.kim.omgchat.model.cqrs.command.user;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * 用户认证command
 *
 * @author kc
 */
@Getter
@EqualsAndHashCode
public class UserSignInCmd {
    private final String email;
    private final String password;

    public UserSignInCmd(String email, String password) {
        if (StringUtils.isBlank(email)) {
            throw new IllegalArgumentException("帐号为空");
        }
        if (StringUtils.isBlank(password)) {
            throw new IllegalArgumentException("密码为空");
        }
        this.email = email;
        this.password = password;
    }
}
