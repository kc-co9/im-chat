package com.co.kc.imchat.domain.message.transformer;

import com.co.kc.imchat.domain.message.model.ImMessageType;
import com.co.kc.imchat.domain.message.model.ImMessageTypeEnum;

/**
 * Domain-level message conversions that must not depend on application or infrastructure mappers.
 */
public final class ImMessageDomainTransformer {
    private ImMessageDomainTransformer() {
    }

    public static ImMessageTypeEnum imMessageTypeEnumFrom(ImMessageType type) {
        if (type == null) {
            return null;
        }
        return ImMessageTypeEnum.valueOf(type.name());
    }
}
