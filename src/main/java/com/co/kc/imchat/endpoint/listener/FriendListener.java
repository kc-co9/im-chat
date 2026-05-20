package com.co.kc.imchat.endpoint.listener;

import com.co.kc.imchat.application.FriendAppService;
import com.co.kc.imchat.domain.friend.FriendAddedEvent;
import com.co.kc.imchat.domain.friend.FriendRemovedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FriendListener {
    private final FriendAppService friendAppService;

    @EventListener
    public void onFriendAdded(FriendAddedEvent event) {
        friendAppService.onFriendAdded(event);
    }

    @EventListener
    public void onFriendDeleted(FriendRemovedEvent event) {
        friendAppService.onFriendRemoved(event);
    }
}
