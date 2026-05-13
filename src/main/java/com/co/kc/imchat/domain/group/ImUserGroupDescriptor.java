package com.co.kc.imchat.domain.group;

import com.co.kc.imchat.domain.chat.ImGroupChat;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 群组描述-值对象
 */
@Getter
@RequiredArgsConstructor
public class ImUserGroupDescriptor {
    private final ImGroupId id;
    private final ImGroupName name;
    private final ImGroupChat chat;
}
