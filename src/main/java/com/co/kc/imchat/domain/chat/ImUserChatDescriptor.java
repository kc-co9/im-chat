package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.message.ImMessage;
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
