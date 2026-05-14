package com.co.kc.imchat.model.io.group;

import com.co.kc.imchat.model.io.WsRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GroupMessageRevokeRequest extends WsRequest {
    private Long chatId;
    private Long messageId;
}
