package com.co.kc.imchat.interfaces.support.context;

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
