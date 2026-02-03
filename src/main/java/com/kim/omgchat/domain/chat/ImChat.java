package com.kim.omgchat.domain.chat;

import com.kim.omgchat.domain.shared.Identification;
import com.kim.omgchat.domain.shared.Validator;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 聊天-领域模型
 */
@Data
@EqualsAndHashCode(callSuper = false)
public abstract class ImChat extends Identification implements Validator {
    private ImChatId id;
    private ImChatName name;
    private ImChatType type;
}
