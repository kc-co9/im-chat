package com.co.kc.imchat.application.model.cqrs.command.group;

public record GroupMemberAliasChangeCmd(
        /* 成员用户ID */
        Long userId,
        /* 群ID */
        Long groupId,
        /* 群内昵称 */
        String userAlias
) {
}
