package com.co.kc.imchat.application.model.cqrs.command.friend;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FriendAddCmd {
    private Long userId;
    private Long friendUserId;
}
