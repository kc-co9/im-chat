package com.kim.omgchat.endpoint.listener;

import com.kim.omgchat.application.ImPrivateAppService;
import com.kim.omgchat.domain.message.ImMessageReadEvent;
import com.kim.omgchat.domain.message.ImMessageReceivedEvent;
import com.kim.omgchat.domain.message.ImMessageRevokedEvent;
import com.kim.omgchat.domain.message.ImMessageSentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImPrivateMessageListener {
    private final ImPrivateAppService imPrivateAppService;

    @EventListener
    public void onMessageSent(ImMessageSentEvent event) {
        imPrivateAppService.onMessageSent(event);
    }

    @EventListener
    public void onMessageReceived(ImMessageReceivedEvent event) {
        imPrivateAppService.onMessageReceived(event);
    }

    @EventListener
    public void onMessageRead(ImMessageReadEvent event) {
        imPrivateAppService.onMessageRead(event);
    }

    @EventListener
    public void onMessageRevoked(ImMessageRevokedEvent event) {
        imPrivateAppService.onMessageRevoked(event);
    }
}
