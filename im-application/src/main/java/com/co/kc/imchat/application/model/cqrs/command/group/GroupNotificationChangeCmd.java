package com.co.kc.imchat.application.model.cqrs.command.group;

public record GroupNotificationChangeCmd(
        /* 操作用户ID */
        Long userId,
        /* 群ID */
        Long groupId,
        /* 群公告 */
        String notification
) {
}
