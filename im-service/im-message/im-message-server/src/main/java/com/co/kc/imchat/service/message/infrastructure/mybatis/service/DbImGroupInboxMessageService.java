package com.co.kc.imchat.service.message.infrastructure.mybatis.service;

import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImGroupInboxMessage;
import com.co.kc.imchat.service.message.infrastructure.mybatis.mapper.DbImGroupInboxMessageMapper;
import com.co.kc.imchat.plugin.datasource.dao.BaseMybatisService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DbImGroupInboxMessageService extends BaseMybatisService<DbImGroupInboxMessageMapper, DbImGroupInboxMessage> {

    public Optional<DbImGroupInboxMessage> getByChatUserMessageId(Long chatId, Long userId, Long messageId) {
        return getFirst(getQueryWrapper()
                .eq(DbImGroupInboxMessage::getChatId, chatId)
                .eq(DbImGroupInboxMessage::getUserId, userId)
                .eq(DbImGroupInboxMessage::getMessageId, messageId));
    }

    public Optional<DbImGroupInboxMessage> getByChatUserToken(Long chatId, Long userId, String token) {
        return getFirst(getQueryWrapper()
                .eq(DbImGroupInboxMessage::getChatId, chatId)
                .eq(DbImGroupInboxMessage::getUserId, userId)
                .eq(DbImGroupInboxMessage::getToken, token));
    }

    public List<DbImGroupInboxMessage> listByGroupIdAndMessageId(Long groupId, Long messageId) {
        return list(getQueryWrapper()
                .eq(DbImGroupInboxMessage::getGroupId, groupId)
                .eq(DbImGroupInboxMessage::getMessageId, messageId));
    }
}
