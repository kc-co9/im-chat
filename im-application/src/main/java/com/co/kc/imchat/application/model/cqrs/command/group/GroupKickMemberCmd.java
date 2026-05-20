package com.co.kc.imchat.application.model.cqrs.command.group;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GroupKickMemberCmd {
    private Long userId;
    private Long groupId;
    private Long memberUserId;
}
