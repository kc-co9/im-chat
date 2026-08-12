package com.co.kc.imchat.service.social.model.cqrs.command.group;

public record GroupMemberAliasChangeCmd(
        /* 成员用户ID */
        Long userId,
        /* 群ID */
        Long groupId,
        /* 群内昵称 */
        String userAlias
) {
}
