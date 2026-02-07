package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.message.ImMessage;
import lombok.Data;

@Data
public class ImChatLastMessage {
    private ImChatId chatId;
    private ImMessage message;
}
