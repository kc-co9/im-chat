package com.co.kc.imchat.model.cqrs.command.friend;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FriendAddCmd {
    private Long userId;
    private Long friendId;
}
