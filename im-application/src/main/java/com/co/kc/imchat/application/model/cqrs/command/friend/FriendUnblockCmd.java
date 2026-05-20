package com.co.kc.imchat.application.model.cqrs.command.friend;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FriendUnblockCmd {
    private Long userId;
    private Long friendUserId;
}
