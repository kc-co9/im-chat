package com.co.kc.imchat.domain.group.event;

import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.group.model.GroupMember;
import com.co.kc.imchat.domain.shared.event.DomainEvent;
import com.co.kc.imchat.domain.user.model.UserId;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class GroupCreatedEvent implements DomainEvent {
    private final GroupId groupId;
    private final UserId ownerId;
    private final List<GroupMember> members;
    private final LocalDateTime createTime = LocalDateTime.now();

    @Override
    public LocalDateTime occurredOn() {
        return createTime;
    }
}
