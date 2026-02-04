package com.co.kc.imchat.endpoint.listener;

import com.co.kc.imchat.application.ImGroupAppService;
import com.co.kc.imchat.domain.message.ImGroupMessageRevokedEvent;
import com.co.kc.imchat.domain.message.ImGroupMessageSentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImGroupMessageListener {
    private final ImGroupAppService imGroupAppService;

    @EventListener
    public void onMessageSent(ImGroupMessageSentEvent event) {
        imGroupAppService.onMessageSent(event);
    }

    @EventListener
    public void onMessageRevoked(ImGroupMessageRevokedEvent event) {
        imGroupAppService.onMessageRevoked(event);
    }
}
