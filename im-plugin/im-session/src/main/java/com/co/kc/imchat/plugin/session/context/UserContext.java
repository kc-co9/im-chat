package com.co.kc.imchat.plugin.session.context;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserContext {
    private Long userId;

    private String email;

    private String username;
}
