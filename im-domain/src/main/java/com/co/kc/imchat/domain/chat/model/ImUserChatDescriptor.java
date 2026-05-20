package com.co.kc.imchat.domain.chat.model;

import com.co.kc.imchat.domain.message.model.ImMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImUserChatDescriptor {
    private ImChatId chatId;
    private ImChatName chatName;
    private ImChatType chatType;
    private ImMessage chatLastMessage;
    private LocalDateTime activeTime;
}
