package com.co.kc.imchat.application.model.cqrs.query.friend;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FriendListQuery {
    private Long userId;
}
