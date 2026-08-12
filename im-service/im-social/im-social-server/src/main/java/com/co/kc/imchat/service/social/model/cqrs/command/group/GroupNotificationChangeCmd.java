package com.co.kc.imchat.service.social.model.cqrs.command.group;

public record GroupNotificationChangeCmd(
        /* 操作用户ID */
        Long userId,
        /* 群ID */
        Long groupId,
        /* 群公告 */
        String notification
) {
}
