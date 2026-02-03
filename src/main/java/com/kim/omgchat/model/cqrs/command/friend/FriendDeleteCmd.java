package com.kim.omgchat.model.cqrs.command.friend;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FriendDeleteCmd {
    private Long userId;
    private Long friendId;
}
