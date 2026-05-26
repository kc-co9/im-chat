package com.co.kc.imchat.domain.group.model;

import com.co.kc.imchat.domain.user.model.UserId;

import java.time.LocalDateTime;

/**
 * 值对象：群成员描述。
 */
public record MemberDescriptor(UserId userId, MemberDisplayName displayName, LocalDateTime joinTime) {
}
