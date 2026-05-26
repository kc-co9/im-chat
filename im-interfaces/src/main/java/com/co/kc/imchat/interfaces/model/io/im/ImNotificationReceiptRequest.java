package com.co.kc.imchat.interfaces.model.io.im;

import com.co.kc.imchat.interfaces.model.enums.ImNotificationReceiptTypeEnum;
import com.co.kc.imchat.interfaces.model.io.WsRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImNotificationReceiptRequest extends WsRequest {
    private Long chatId;
    private Long messageId;
    private ImNotificationReceiptTypeEnum receiptType;
}
