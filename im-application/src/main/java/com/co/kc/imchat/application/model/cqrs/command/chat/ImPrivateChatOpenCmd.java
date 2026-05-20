package com.co.kc.imchat.application.model.cqrs.command.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImPrivateChatOpenCmd {
    private Long userId;
    private Long peerUserId;
}
