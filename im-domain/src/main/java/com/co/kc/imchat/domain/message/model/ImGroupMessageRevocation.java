package com.co.kc.imchat.domain.message.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class ImGroupMessageRevocation {
    private final List<ImGroupInboxMessage> messages;
    private final ImGroupInboxMessage senderMessage;

    public ImGroupMessageRevocation(List<ImGroupInboxMessage> messages, ImGroupInboxMessage senderMessage) {
        this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
        this.senderMessage = senderMessage;
    }
}
