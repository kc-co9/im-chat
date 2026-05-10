package com.co.kc.imchat.infrastructure.mybatis.service;

import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateInboxMessage;
import com.co.kc.imchat.infrastructure.mybatis.mapper.DbImPrivateInboxMessageMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DbImPrivateInboxMessageService extends BaseMybatisService<DbImPrivateInboxMessageMapper, DbImPrivateInboxMessage> {

    public Optional<DbImPrivateInboxMessage> getByChatIdAndMessageToken(Long chatId, String messageToken) {
        return getFirst(getQueryWrapper()
                .eq(DbImPrivateInboxMessage::getChatId, chatId)
                .eq(DbImPrivateInboxMessage::getToken, messageToken));
    }

    public Optional<DbImPrivateInboxMessage> getByChatIdAndMessageId(Long chatId, Long messageId) {
        return getFirst(getQueryWrapper()
                .eq(DbImPrivateInboxMessage::getChatId, chatId)
                .eq(DbImPrivateInboxMessage::getMessageId, messageId));
    }

    public Optional<DbImPrivateInboxMessage> getByUserIdAndMessageId(Long userId, Long messageId) {
        return getFirst(getQueryWrapper()
                .eq(DbImPrivateInboxMessage::getUserId, userId)
                .eq(DbImPrivateInboxMessage::getMessageId, messageId));
    }

    public List<DbImPrivateInboxMessage> listByMessageId(Long messageId) {
        return list(getQueryWrapper().eq(DbImPrivateInboxMessage::getMessageId, messageId));
    }

    public long countByMessageId(Long messageId) {
        return count(getQueryWrapper().eq(DbImPrivateInboxMessage::getMessageId, messageId));
    }
}
