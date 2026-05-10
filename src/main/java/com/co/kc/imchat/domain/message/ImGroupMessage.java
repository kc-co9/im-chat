package com.co.kc.imchat.domain.message;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.user.UserId;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImGroupMessage extends ImMessage {
    private ImChatId chatId;
    private ImGroupMessageStatus status;

    public void revoke(UserId userId) {

    }
}
