package com.co.kc.imchat.service.social.model.cqrs.query.group;

public record GroupDetailQuery(
        /* 用户ID */
        Long userId,
        /* 群ID */
        Long groupId
) {
}
