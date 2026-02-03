package com.kim.omgchat.model.io.user;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserDetailResponse {
    private Long userId;

    private String email;

    private String username;
}
