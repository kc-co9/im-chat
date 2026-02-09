package com.co.kc.imchat.infrastructure.mybatis.service;

import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImChatLastMessage;
import com.co.kc.imchat.infrastructure.mybatis.enums.DbImChatType;
import com.co.kc.imchat.infrastructure.mybatis.mapper.DbImChatLastMessageMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 聊天最后消息表(DbImChatLastMessage)表服务接口
 *
 * @author kc
 * @since 2026-02-03 11:11:22
 */
@Service
public class DbImChatLastMessageService extends BaseMybatisService<DbImChatLastMessageMapper, DbImChatLastMessage> {
    public Optional<DbImChatLastMessage> getByChatId(Long chatId) {
        return getFirst(getQueryWrapper().eq(DbImChatLastMessage::getChatId, chatId));
    }

    public List<DbImChatLastMessage> getListByChatIds(DbImChatType chatType, List<Long> chatIds) {
        if (Objects.isNull(chatType) || CollectionUtils.isEmpty(chatIds)) {
            return Collections.emptyList();
        }
        return list(getQueryWrapper().eq(DbImChatLastMessage::getChatType, chatType).in(DbImChatLastMessage::getChatId, chatIds));
    }

    public boolean isExistPrivateLastMessage(Long chatId) {
        return isExist(getQueryWrapper()
                .eq(DbImChatLastMessage::getChatType, DbImChatType.PRIVATE)
                .eq(DbImChatLastMessage::getChatId, chatId));
    }

    public boolean isExistGroupLastMessage(Long chatId) {
        return isExist(getQueryWrapper()
                .eq(DbImChatLastMessage::getChatType, DbImChatType.GROUP)
                .eq(DbImChatLastMessage::getChatId, chatId));
    }

    public void updatePrivateLastMessage(Long chatId, Long messageId) {
        update(getUpdateWrapper()
                .set(DbImChatLastMessage::getMessageId, messageId)
                .eq(DbImChatLastMessage::getChatType, DbImChatType.PRIVATE)
                .eq(DbImChatLastMessage::getChatId, chatId)
        );
    }

    public void updateGroupLastMessage(Long chatId, Long messageId) {
        update(getUpdateWrapper()
                .set(DbImChatLastMessage::getMessageId, messageId)
                .eq(DbImChatLastMessage::getChatType, DbImChatType.GROUP)
                .eq(DbImChatLastMessage::getChatId, chatId)
        );
    }
}
