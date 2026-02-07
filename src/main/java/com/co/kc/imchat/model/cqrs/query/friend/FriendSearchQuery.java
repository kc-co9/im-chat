package com.co.kc.imchat.model.cqrs.query.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendSearchQuery {
    private String email;
}
