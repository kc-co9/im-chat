package com.co.kc.imchat.domain.group;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class GroupCreation {
    private final Group group;
    private final List<GroupMember> members;
}
