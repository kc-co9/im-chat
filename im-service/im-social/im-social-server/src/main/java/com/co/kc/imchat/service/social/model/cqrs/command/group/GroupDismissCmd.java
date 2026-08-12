package com.co.kc.imchat.service.social.model.cqrs.command.group;

public record GroupDismissCmd(
        /* 操作用户ID */
        Long userId,
        /* 群ID */
        Long groupId
) {
}
