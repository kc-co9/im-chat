package com.co.kc.imchat.domain.friend;

import com.co.kc.imchat.domain.shared.DomainEvent;
import com.co.kc.imchat.domain.user.UserId;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
public class FriendRemovedEvent implements DomainEvent {
    private final UserId userId;
    private final UserId friendUserId;
    private final LocalDateTime createTime = LocalDateTime.now();

    @Override
    public LocalDateTime occurredOn() {
        return createTime;
    }
}
