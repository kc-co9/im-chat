package com.kim.omgchat.domain.message;

import com.kim.omgchat.domain.chat.ImChatId;
import com.kim.omgchat.domain.user.UserId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImGroupMessage extends ImMessage {
    private ImChatId receiveGroupId;

}
