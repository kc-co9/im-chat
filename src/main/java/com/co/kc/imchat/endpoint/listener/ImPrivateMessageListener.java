package com.co.kc.imchat.endpoint.listener;

import com.co.kc.imchat.application.ImPrivateAppService;
import com.co.kc.imchat.domain.message.ImPrivateMessageReadEvent;
import com.co.kc.imchat.domain.message.ImPrivateMessageReceivedEvent;
import com.co.kc.imchat.domain.message.ImPrivateMessageRevokedEvent;
import com.co.kc.imchat.domain.message.ImPrivateMessageSentEvent;
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
