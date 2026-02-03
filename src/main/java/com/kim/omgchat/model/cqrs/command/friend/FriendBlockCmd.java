package com.kim.omgchat.model.cqrs.command.friend;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FriendBlockCmd {
    private Long userId;
    private Long friendId;
}
