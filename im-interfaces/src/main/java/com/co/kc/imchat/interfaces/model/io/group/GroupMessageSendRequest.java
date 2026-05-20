package com.co.kc.imchat.interfaces.model.io.group;

import com.co.kc.imchat.domain.message.model.ImMessageTypeEnum;
import com.co.kc.imchat.interfaces.model.io.WsRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GroupMessageSendRequest extends WsRequest {
    private Long chatId;
    private String messageToken;
    private ImMessageTypeEnum messageType;
    private String messageContent;
}
