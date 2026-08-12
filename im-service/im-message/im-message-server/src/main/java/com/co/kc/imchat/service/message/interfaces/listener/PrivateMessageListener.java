package com.co.kc.imchat.service.message.interfaces.listener;

import com.co.kc.imchat.service.message.application.PrivateMessageAppService;
import com.co.kc.imchat.service.message.domain.message.event.ImPrivateMessageRevokedEvent;
import com.co.kc.imchat.service.message.domain.message.event.ImPrivateMessageSentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PrivateMessageListener {
    private final PrivateMessageAppService privateMessageAppService;

    @EventListener
    public void onMessageSent(ImPrivateMessageSentEvent event) {
        privateMessageAppService.onMessageSent(event);
    }

    @EventListener
    public void onMessageRevoked(ImPrivateMessageRevokedEvent event) {
        privateMessageAppService.onMessageRevoked(event);
    }
}
