package com.co.kc.imchat.interfaces.model.io.im;

import com.co.kc.imchat.interfaces.model.io.WsRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImPrivateMessageRevokeRequest extends WsRequest {
    private Long chatId;
    private Long messageId;
}
