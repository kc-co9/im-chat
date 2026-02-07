package com.co.kc.imchat.infrastructure.mybatis.service;

import com.co.kc.imchat.infrastructure.mybatis.entity.DbImChatLastMessage;
import com.co.kc.imchat.infrastructure.mybatis.mapper.DbImChatLastMessageMapper;
import org.springframework.stereotype.Service;

import java.util.List;
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

    public List<DbImChatLastMessage> getListByChatIds(List<Long> chatIds) {
        return list(getQueryWrapper().in(DbImChatLastMessage::getChatId, chatIds));
    }
}
