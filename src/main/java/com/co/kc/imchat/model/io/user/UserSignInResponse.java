package com.co.kc.imchat.model.io.user;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserSignInResponse {
    private Long userId;
    private String token;
}
