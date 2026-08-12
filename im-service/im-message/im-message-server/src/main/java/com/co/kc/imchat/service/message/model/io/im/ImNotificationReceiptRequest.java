package com.co.kc.imchat.service.message.model.io.im;

import com.co.kc.imchat.service.message.model.enums.ImNotificationReceiptTypeEnum;
import lombok.Data;

@Data
public class ImNotificationReceiptRequest {
    private Long chatId;
    private Long messageId;
    private ImNotificationReceiptTypeEnum receiptType;
}
