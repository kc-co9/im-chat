package com.co.kc.imchat.domain.group.event;

import com.co.kc.imchat.domain.group.model.GroupId;
import com.co.kc.imchat.domain.shared.event.DomainEvent;
import com.co.kc.imchat.domain.user.model.UserId;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
public class GroupDismissedEvent implements DomainEvent {
    private final GroupId groupId;
    private final UserId ownerId;
    private final LocalDateTime createTime = LocalDateTime.now();

    @Override
    public LocalDateTime occurredOn() {
        return createTime;
    }
}
