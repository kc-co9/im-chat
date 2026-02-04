package com.co.kc.imchat.model.io.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/5 17:06
 */
@Data
public class UserSignUpRequest {
    @NotBlank(message = "邮箱不能为空")
    private String email;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
