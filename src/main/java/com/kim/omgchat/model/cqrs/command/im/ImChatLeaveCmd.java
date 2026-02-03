package com.kim.omgchat.model.cqrs.command.im;

import lombok.Data;

@Data
public class ImChatLeaveCmd {
    private Long userId;
}
