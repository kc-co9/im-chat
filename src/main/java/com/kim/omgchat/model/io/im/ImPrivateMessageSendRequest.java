package com.kim.omgchat.model.io.im;

import com.kim.omgchat.model.enums.ImMessageTypeEnum;
import com.kim.omgchat.model.io.WsRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImPrivateMessageSendRequest extends WsRequest {
    private Long chatId;
    private Long receiverId;
    private String messageToken;
    private ImMessageTypeEnum messageType;
    private String messageContent;
}
