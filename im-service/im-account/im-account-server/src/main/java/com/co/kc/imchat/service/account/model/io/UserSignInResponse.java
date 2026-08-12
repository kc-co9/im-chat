package com.co.kc.imchat.service.account.model.io;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserSignInResponse {
    private Long userId;
    private String token;
}
