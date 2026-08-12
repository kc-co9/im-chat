package com.co.kc.imchat.service.social.domain.group.model;

import java.util.List;

/**
 * 值对象：群组创建结果。
 */
public record GroupCreation(Group group, List<GroupMember> members) {
}
