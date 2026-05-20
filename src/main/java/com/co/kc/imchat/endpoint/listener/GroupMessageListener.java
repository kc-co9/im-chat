package com.co.kc.imchat.endpoint.listener;

import com.co.kc.imchat.application.GroupMessageAppService;
import com.co.kc.imchat.domain.message.ImGroupMessageRevokedEvent;
import com.co.kc.imchat.domain.message.ImGroupMessageSentEvent;
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
