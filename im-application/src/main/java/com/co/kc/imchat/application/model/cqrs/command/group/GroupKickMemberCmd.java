package com.co.kc.imchat.application.model.cqrs.command.group;

public record GroupKickMemberCmd(
        /* 操作用户ID */
        Long userId,
        /* 群ID */
        Long groupId,
        /* 被移出成员用户ID */
        Long memberUserId
) {
}
