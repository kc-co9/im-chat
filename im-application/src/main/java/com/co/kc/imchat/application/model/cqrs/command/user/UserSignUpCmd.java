package com.co.kc.imchat.application.model.cqrs.command.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserSignUpCmd {
    private String email;
    private String username;
    private String password;
}
