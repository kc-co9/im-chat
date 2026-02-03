package com.kim.omgchat.endpoint.listener;

import com.kim.omgchat.application.PrivateAppService;
import com.kim.omgchat.domain.message.ImMessageReadEvent;
import com.kim.omgchat.domain.message.ImMessageRevokedEvent;
import com.kim.omgchat.domain.message.ImMessageSentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImPrivateMessageListener {
    private final PrivateAppService privateAppService;

    @EventListener
    public void onMessageSent(ImMessageSentEvent event) {
        privateAppService.onMessageSent(event);
    }

    @EventListener
    public void onMessageRead(ImMessageReadEvent event) {
        privateAppService.onMessageRead(event);
    }

    @EventListener
    public void onMessageRevoked(ImMessageRevokedEvent event) {
        privateAppService.onMessageRevoked(event);
    }
}
