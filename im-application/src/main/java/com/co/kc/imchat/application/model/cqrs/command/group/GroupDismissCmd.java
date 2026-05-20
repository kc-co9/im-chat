package com.co.kc.imchat.application.model.cqrs.command.group;

public record GroupDismissCmd(
        /* 操作用户ID */
        Long userId,
        /* 群ID */
        Long groupId
) {
}
