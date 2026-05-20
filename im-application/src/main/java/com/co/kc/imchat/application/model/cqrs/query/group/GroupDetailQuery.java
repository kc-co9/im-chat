package com.co.kc.imchat.application.model.cqrs.query.group;

public record GroupDetailQuery(
        /* 用户ID */
        Long userId,
        /* 群ID */
        Long groupId
) {
}
