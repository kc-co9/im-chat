package com.co.kc.imchat.model.cqrs.command.group;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GroupTransferOwnerCmd {
    private Long userId;
    private Long groupId;
    private Long newOwnerId;
}
