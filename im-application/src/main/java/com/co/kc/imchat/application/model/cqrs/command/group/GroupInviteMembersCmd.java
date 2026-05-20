package com.co.kc.imchat.application.model.cqrs.command.group;

import java.util.List;

public record GroupInviteMembersCmd(
        /* 邀请人用户ID */
        Long userId,
        /* 群ID */
        Long groupId,
        /* 被邀请用户ID列表 */
        List<Long> inviteeIds
) {
}
