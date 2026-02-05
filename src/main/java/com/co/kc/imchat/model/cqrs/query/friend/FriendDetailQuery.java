package com.co.kc.imchat.model.cqrs.query.friend;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FriendDetailQuery {
    private Long userId;
    private Long friendUserId;
}
