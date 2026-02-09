package com.co.kc.imchat.infrastructure.mybatis.service;

import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.mapper.DbImGroupChatMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 群聊表(DbImGroupChat)表服务接口
 *
 * @author kc
 * @since 2026-02-03 11:11:22
 */
@Service
public class DbImGroupChatService extends BaseMybatisService<DbImGroupChatMapper, DbImGroupChat> {
    public Optional<DbImGroupChat> getByChatId(Long chatId) {
        return getFirst(this.getQueryWrapper().eq(DbImGroupChat::getChatId, chatId));
    }

    public List<DbImGroupChat> getListByUserId(Long userId) {
        return this.baseMapper.selectListByUserId(userId);
    }
}
