package com.co.kc.imchat.interfaces.model.io.user;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class UserSignUpRequest {
    @NotBlank(message = "邮箱不能为空")
    private String email;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
