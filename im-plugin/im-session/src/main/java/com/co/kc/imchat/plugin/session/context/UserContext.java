package com.co.kc.imchat.plugin.session.context;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserContext {
    private Long userId;

    private String email;

    private String username;

    private String sessionVersion;

    public UserContext(Long userId, String email, String username) {
        this(userId, email, username, null);
    }

    public UserContext(Long userId, String email, String username, String sessionVersion) {
        this.userId = userId;
        this.email = email;
        this.username = username;
        this.sessionVersion = sessionVersion;
    }
}
