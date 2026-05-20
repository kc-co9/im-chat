package com.co.kc.imchat.model.cqrs.command.friend;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FriendAliasChangeCmd {
    private Long userId;
    private Long friendUserId;
    private String friendAlias;
}
