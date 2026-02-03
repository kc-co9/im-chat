package com.kim.omgchat.model.io.im;

import com.kim.omgchat.model.io.WsRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImPrivateMessageRevokeRequest extends WsRequest {
    private Long chatId;
    private Long messageId;
}
