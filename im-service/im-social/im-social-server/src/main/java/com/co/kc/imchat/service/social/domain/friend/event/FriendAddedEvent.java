package com.co.kc.imchat.service.social.domain.friend.event;

import com.co.kc.imchat.common.domain.shared.event.DomainEvent;
import com.co.kc.imchat.common.domain.user.model.UserId;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
public class FriendAddedEvent implements DomainEvent {
    private final UserId userId;
    private final UserId friendUserId;
    private final LocalDateTime createTime = LocalDateTime.now();

    @Override
    public LocalDateTime occurredOn() {
        return createTime;
    }
}
