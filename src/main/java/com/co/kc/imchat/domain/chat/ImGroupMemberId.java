package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.user.UserId;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ImGroupMemberId {
    private final ImChatId chatId;
    private final UserId userId;

    public ImGroupMemberId(ImChatId chatId, UserId userId) {
        if (chatId == null || userId == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        this.chatId = chatId;
        this.userId = userId;
    }
}
