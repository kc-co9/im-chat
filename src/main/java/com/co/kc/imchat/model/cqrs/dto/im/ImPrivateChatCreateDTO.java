package com.co.kc.imchat.model.cqrs.dto.im;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 创建私聊结果：双方各有一个独立的 chatId。
 */
@Data
@AllArgsConstructor
public class ImPrivateChatCreateDTO {
    /**
     * 当前发起方（命令中的 sender）对应的 chatId
     */
    private Long chatId;
}