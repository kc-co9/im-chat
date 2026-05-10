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

    @Override
    public void validate() {
        super.validate();
        if (chatId == null || status == null) {
            throw new IllegalStateException("群消息缺少 chatId 或 status");
        }
    }

    public void revoke(UserId userId) {

    }
}
