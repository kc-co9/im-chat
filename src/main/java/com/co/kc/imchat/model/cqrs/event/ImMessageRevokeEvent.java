package com.co.kc.imchat.model.cqrs.event;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImMessageRevokeEvent implements Serializable {

    private String chatId;

    private String serverMsgId;

    private Long receiverId;

    private String clientMsgId;

    private Integer revokeStatus;

}
