package com.kim.omgchat.domain.message;

import com.kim.omgchat.domain.chat.ImChatId;
import com.kim.omgchat.domain.user.UserId;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImGroupMessage extends ImMessage {
    private ImChatId chatId;

    public void revoke(UserId userId) {

    }
}
