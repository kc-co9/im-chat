package com.co.kc.imchat.service.social.model.cqrs.command.group;

public record GroupTransferOwnerCmd(
        /* 当前群主用户ID */
        Long userId,
        /* 群ID */
        Long groupId,
        /* 新群主用户ID */
        Long newOwnerId
) {
}
