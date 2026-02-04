package com.co.kc.imchat.domain.chat;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 私聊-领域模型
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ImPrivateChat extends ImChat {
    private ImPrivatePair pair;
}
