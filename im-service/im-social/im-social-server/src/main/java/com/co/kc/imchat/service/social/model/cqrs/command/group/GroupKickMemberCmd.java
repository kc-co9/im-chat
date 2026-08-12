package com.co.kc.imchat.service.social.model.cqrs.command.group;

public record GroupKickMemberCmd(
        /* 操作用户ID */
        Long userId,
        /* 群ID */
        Long groupId,
        /* 被移出成员用户ID */
        Long memberUserId
) {
}
