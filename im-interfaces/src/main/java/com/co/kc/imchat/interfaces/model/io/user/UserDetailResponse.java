package com.co.kc.imchat.interfaces.model.io.user;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserDetailResponse {
    private Long userId;

    private String email;

    private String username;
}
