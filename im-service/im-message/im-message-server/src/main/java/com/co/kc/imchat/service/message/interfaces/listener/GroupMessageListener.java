package com.co.kc.imchat.service.message.interfaces.listener;

import com.co.kc.imchat.service.message.application.GroupMessageAppService;
import com.co.kc.imchat.service.message.domain.message.event.ImGroupMessageRevokedEvent;
import com.co.kc.imchat.service.message.domain.message.event.ImGroupMessageSentEvent;
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
