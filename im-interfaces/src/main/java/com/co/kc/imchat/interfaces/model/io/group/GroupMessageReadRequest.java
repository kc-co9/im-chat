package com.co.kc.imchat.interfaces.model.io.group;

import com.co.kc.imchat.interfaces.model.io.WsRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GroupMessageReadRequest extends WsRequest {
    private Long chatId;
    private Long messageId;
}
