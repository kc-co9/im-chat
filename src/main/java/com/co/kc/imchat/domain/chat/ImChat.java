package com.co.kc.imchat.domain.chat;

import com.co.kc.imchat.domain.shared.Identification;
import com.co.kc.imchat.domain.shared.Validator;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 聊天-领域模型
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ImChat extends Identification implements Validator {
    private ImChatId id;
    private ImChatName name;
    private ImChatType type;
}
