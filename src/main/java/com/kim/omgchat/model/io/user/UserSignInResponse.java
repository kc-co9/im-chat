package com.kim.omgchat.model.io.user;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserSignInResponse {
    private String token;
}
