package com.kim.omgchat.infrastructure.context;

import lombok.Data;

@Data
public class UserContext {
    private Long userId;

    private String email;

    private String username;
}
