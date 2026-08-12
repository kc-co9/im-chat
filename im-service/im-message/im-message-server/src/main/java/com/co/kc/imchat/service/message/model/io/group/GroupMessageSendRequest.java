package com.co.kc.imchat.service.message.model.io.group;

import com.co.kc.imchat.service.message.domain.message.model.ImMessageTypeEnum;
import lombok.Data;

@Data
public class GroupMessageSendRequest {
    private Long chatId;
    private String messageToken;
    private ImMessageTypeEnum messageType;
    private String messageContent;
}
