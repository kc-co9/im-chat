package com.co.kc.imchat.model.io.im;

import com.co.kc.imchat.model.enums.ImMessageTypeEnum;
import com.co.kc.imchat.model.io.WsRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImGroupMessageSendRequest extends WsRequest {
    private Long chatId;
    private String messageToken;
    private ImMessageTypeEnum messageType;
    private String messageContent;
}
