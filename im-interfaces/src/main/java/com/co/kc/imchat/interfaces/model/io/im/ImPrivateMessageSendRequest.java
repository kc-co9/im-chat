package com.co.kc.imchat.interfaces.model.io.im;

import com.co.kc.imchat.domain.message.model.ImMessageTypeEnum;
import com.co.kc.imchat.interfaces.model.io.WsRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImPrivateMessageSendRequest extends WsRequest {
    private Long chatId;
    private String messageToken;
    private ImMessageTypeEnum messageType;
    private String messageContent;
}
