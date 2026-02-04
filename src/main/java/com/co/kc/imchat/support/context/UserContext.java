package com.co.kc.imchat.support.context;

import lombok.Data;

@Data
public class UserContext {
    private Long userId;

    private String email;

    private String username;
}
