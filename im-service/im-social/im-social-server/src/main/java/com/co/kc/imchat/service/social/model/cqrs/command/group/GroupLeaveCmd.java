package com.co.kc.imchat.service.social.model.cqrs.command.group;

public record GroupLeaveCmd(
        /* 退群用户ID */
        Long userId,
        /* 群ID */
        Long groupId
) {
}
