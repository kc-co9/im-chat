package com.co.kc.imchat.endpoint.listener;

import com.co.kc.imchat.application.PrivateMessageAppService;
import com.co.kc.imchat.domain.message.ImPrivateMessageReceivedEvent;
import com.co.kc.imchat.domain.message.ImPrivateMessageRevokedEvent;
import com.co.kc.imchat.domain.message.ImPrivateMessageSentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImPrivateMessageListener {
    private final PrivateMessageAppService privateMessageAppService;

    @EventListener
    public void onMessageSent(ImPrivateMessageSentEvent event) {
        privateMessageAppService.onMessageSent(event);
    }

    @EventListener
    public void onMessageReceived(ImPrivateMessageReceivedEvent event) {
        privateMessageAppService.onMessageReceived(event);
    }

    @EventListener
    public void onMessageRevoked(ImPrivateMessageRevokedEvent event) {
        privateMessageAppService.onMessageRevoked(event);
    }
}
