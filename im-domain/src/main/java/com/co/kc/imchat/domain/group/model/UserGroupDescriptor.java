package com.co.kc.imchat.domain.group.model;

import com.co.kc.imchat.domain.chat.model.ImGroupChat;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 群组描述-值对象
 */
@Getter
@RequiredArgsConstructor
public class UserGroupDescriptor {
    private final GroupId id;
    private final GroupName name;
    private final ImGroupChat chat;
    private final MemberCount memberCount;
}
