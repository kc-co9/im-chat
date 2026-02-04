package com.co.kc.imchat.model.io.im;

import com.co.kc.imchat.model.io.WsRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImPrivateMessageReadRequest extends WsRequest {
    private Long chatId;
    private Long messageId;
}
