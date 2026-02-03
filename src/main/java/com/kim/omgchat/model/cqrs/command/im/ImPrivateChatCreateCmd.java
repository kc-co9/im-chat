package com.kim.omgchat.model.cqrs.command.im;

import lombok.Data;

@Data
public class ImPrivateChatCreateCmd {
    private Long senderId;
    private Long receiverId;
}
