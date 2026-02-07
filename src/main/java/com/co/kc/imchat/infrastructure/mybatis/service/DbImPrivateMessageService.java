package com.co.kc.imchat.infrastructure.mybatis.service;

import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateMessage;
import com.co.kc.imchat.infrastructure.mybatis.mapper.DbImPrivateMessageMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 私聊消息表(DbImPrivateMessage)表服务接口
 *
 * @author kc
 * @since 2026-02-03 11:11:22
 */
@Service
public class DbImPrivateMessageService extends BaseMybatisService<DbImPrivateMessageMapper, DbImPrivateMessage> {

    public Optional<DbImPrivateMessage> getByChatIdAndMessageId(Long chatId, Long messageId) {
        return getFirst(getQueryWrapper()
                .eq(DbImPrivateMessage::getChatId, chatId)
                .eq(DbImPrivateMessage::getMessageId, messageId));
    }

    public Optional<DbImPrivateMessage> getLastMessageByChatId(Long chatId, Long messageId) {
        return getFirst(getQueryWrapper()
                .eq(DbImPrivateMessage::getChatId, chatId)
                .eq(DbImPrivateMessage::getMessageId, messageId));
    }

    public List<DbImPrivateMessage> getLastMessageByChatIds(List<Long> chatIds) {
        return list(getQueryWrapper()
                .in(DbImPrivateMessage::getChatId, chatIds)
                .orderByDesc(DbImPrivateMessage::getSendTime));
    }
}
