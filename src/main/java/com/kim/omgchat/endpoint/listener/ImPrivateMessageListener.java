package com.kim.omgchat.endpoint.listener;

import com.kim.omgchat.application.ImPrivateAppService;
import com.kim.omgchat.domain.message.ImPrivateMessageReadEvent;
import com.kim.omgchat.domain.message.ImPrivateMessageReceivedEvent;
import com.kim.omgchat.domain.message.ImPrivateMessageRevokedEvent;
import com.kim.omgchat.domain.message.ImPrivateMessageSentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImPrivateMessageListener {
    private final ImPrivateAppService imPrivateAppService;

    @EventListener
    public void onMessageSent(ImPrivateMessageSentEvent event) {
        imPrivateAppService.onMessageSent(event);
    }

    @EventListener
    public void onMessageReceived(ImPrivateMessageReceivedEvent event) {
        imPrivateAppService.onMessageReceived(event);
    }

    @EventListener
    public void onMessageRead(ImPrivateMessageReadEvent event) {
        imPrivateAppService.onMessageRead(event);
    }

    @EventListener
    public void onMessageRevoked(ImPrivateMessageRevokedEvent event) {
        imPrivateAppService.onMessageRevoked(event);
    }
}
