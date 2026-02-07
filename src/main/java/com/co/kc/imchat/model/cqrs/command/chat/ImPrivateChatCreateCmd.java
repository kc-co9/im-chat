package com.co.kc.imchat.model.cqrs.command.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImPrivateChatCreateCmd {
    private Long senderId;
    private Long receiverId;
}
