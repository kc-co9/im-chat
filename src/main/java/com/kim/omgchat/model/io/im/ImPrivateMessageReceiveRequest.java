package com.kim.omgchat.model.io.im;

import com.kim.omgchat.model.io.WsRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ImPrivateMessageReceiveRequest extends WsRequest {
    private Long chatId;
    private Long messageId;
}
