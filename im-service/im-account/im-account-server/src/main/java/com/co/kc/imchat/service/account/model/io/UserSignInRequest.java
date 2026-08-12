package com.co.kc.imchat.service.account.model.io;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class UserSignInRequest {
    @NotBlank(message = "邮箱不能为空")
    private String email;

    @NotBlank(message = "密码不能为空")
    private String password;
}
