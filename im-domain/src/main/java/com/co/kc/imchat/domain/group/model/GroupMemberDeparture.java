package com.co.kc.imchat.domain.group.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class GroupMemberDeparture {
    private final Group group;
    private final GroupMember groupMember;
}
