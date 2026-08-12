package com.co.kc.imchat.service.message.domain.chat.model;

import com.co.kc.imchat.service.message.domain.message.model.ImMessage;

import java.time.LocalDateTime;

/**
 * 值对象：用户聊天列表描述。
 */
public record ImUserChatDescriptor(
        ImChatId chatId,
        ImChatName chatName,
        ImChatType chatType,
        ImMessage chatLastMessage,
        LocalDateTime activeTime) {
}
