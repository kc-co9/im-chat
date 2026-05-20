package com.co.kc.imchat.interfaces.endpoint.listener;

import com.co.kc.imchat.application.GroupMessageAppService;
import com.co.kc.imchat.domain.message.event.ImGroupMessageRevokedEvent;
import com.co.kc.imchat.domain.message.event.ImGroupMessageSentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroupMessageListener {
    private final GroupMessageAppService groupMessageAppService;

    @EventListener
    public void onMessageSent(ImGroupMessageSentEvent event) {
        groupMessageAppService.onMessageSent(event);
    }

    @EventListener
    public void onMessageRevoked(ImGroupMessageRevokedEvent event) {
        groupMessageAppService.onMessageRevoked(event);
    }
}
