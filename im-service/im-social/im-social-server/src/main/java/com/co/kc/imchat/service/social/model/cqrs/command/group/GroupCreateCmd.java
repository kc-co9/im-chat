package com.co.kc.imchat.service.social.model.cqrs.command.group;

import java.util.List;

public record GroupCreateCmd(
        /* 群主用户ID */
        Long ownerId,
        /* 初始成员用户ID列表 */
        List<Long> memberIds,
        /* 群名称 */
        String groupName
) {
}
