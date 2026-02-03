package com.kim.omgchat.model.io.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class UserSignInRequest {
    @NotBlank(message = "邮箱不能为空")
    private String email;

    @NotBlank(message = "密码不能为空")
    private String password;
}
