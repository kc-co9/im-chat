package com.co.kc.imchat.application.model.cqrs.dto.im;

import lombok.Data;

@Data
public class ImMessageSendDTO {

    private Long senderId;

    private String chatId;

    private String clientMsgId;

    private Long receiverId;

    private Integer senderRole;

    private Integer msgType;

    private String msgContent;

    private String mediaUrl;

    private Integer audioDuration;

    private String stickerId;

    private String replyMsgId;

    private Integer isUrgent;
}
