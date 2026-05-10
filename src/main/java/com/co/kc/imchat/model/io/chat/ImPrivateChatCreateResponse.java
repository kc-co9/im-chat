package com.co.kc.imchat.model.io.chat;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 创建私聊响应：双方会话 id 相互独立。
 */
@Data
@AllArgsConstructor
public class ImPrivateChatCreateResponse {
    /** 当前登录用户在本私聊中的 chatId */
    private Long chatId;
}
