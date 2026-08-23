package com.co.kc.imchat.service.message.domain.chat.model;

import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.common.utils.AssertUtils;

/** 当前用户正在查看的聊天状态。 */
public record ImChatView(UserId userId, ImChatId chatId) {
    public ImChatView {
        AssertUtils.domainPropNotNull("用户ID不能为空", userId);
        AssertUtils.domainPropNotNull("聊天ID不能为空", chatId);
    }

    public boolean matches(ImChatId expectedChatId) {
        AssertUtils.domainPropNotNull("聊天ID不能为空", expectedChatId);
        return chatId.equals(expectedChatId);
    }
}
