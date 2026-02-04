package com.co.kc.imchat.model.cqrs.event;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImMessageSentEvent implements Serializable {

    private String chatId;

    private String serverMsgId;

    private Long senderId;

    private Long receiverId;
}
