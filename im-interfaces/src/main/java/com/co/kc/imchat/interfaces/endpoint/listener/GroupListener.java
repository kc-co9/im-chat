package com.co.kc.imchat.interfaces.endpoint.listener;

import com.co.kc.imchat.application.GroupAppService;
import com.co.kc.imchat.domain.group.event.GroupCreatedEvent;
import com.co.kc.imchat.domain.group.event.GroupDismissedEvent;
import com.co.kc.imchat.domain.group.event.GroupMemberJoinedEvent;
import com.co.kc.imchat.domain.group.event.GroupMemberRemovedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroupListener {
    private final GroupAppService groupAppService;

    @EventListener
    public void onGroupCreated(GroupCreatedEvent event) {
        groupAppService.onGroupCreated(event);
    }

    @EventListener
    public void onGroupDismissed(GroupDismissedEvent event) {
        groupAppService.onGroupDismissed(event);
    }

    @EventListener
    public void onGroupMemberJoined(GroupMemberJoinedEvent event) {
        groupAppService.onGroupMemberJoined(event);
    }

    @EventListener
    public void onGroupMemberRemoved(GroupMemberRemovedEvent event) {
        groupAppService.onGroupMemberRemoved(event);
    }
}
