package com.co.kc.imchat.domain.message;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
@AllArgsConstructor
public class ImPrivateMessageRevocation {
    private final ImPrivateInboxMessage senderMessage;
    private final ImPrivateInboxMessage receiverMessage;

    public List<ImPrivateInboxMessage> getMessages() {
        return Arrays.asList(senderMessage, receiverMessage);
    }
}
