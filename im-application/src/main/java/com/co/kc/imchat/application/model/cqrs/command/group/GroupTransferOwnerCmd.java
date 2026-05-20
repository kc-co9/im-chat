package com.co.kc.imchat.application.model.cqrs.command.group;

public record GroupTransferOwnerCmd(
        /* 当前群主用户ID */
        Long userId,
        /* 群ID */
        Long groupId,
        /* 新群主用户ID */
        Long newOwnerId
) {
}
