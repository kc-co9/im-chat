package com.kim.omgchat.model.cqrs.query.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserAuthQuery {
    private Long userId;
}
