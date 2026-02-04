package com.kim.omgchat.endpoint.listener;

import com.kim.omgchat.application.ImGroupAppService;
import com.kim.omgchat.domain.message.ImGroupMessageRevokedEvent;
import com.kim.omgchat.domain.message.ImGroupMessageSentEvent;
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
