package com.co.kc.imchat.domain.group.model;

import com.co.kc.imchat.domain.chat.model.ImGroupChat;

/**
 * 值对象：用户群组描述。
 */
public record UserGroupDescriptor(GroupId id, GroupName name, ImGroupChat chat, MemberCount memberCount) {
}
