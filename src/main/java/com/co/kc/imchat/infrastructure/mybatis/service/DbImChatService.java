package com.co.kc.imchat.infrastructure.mybatis.service;

import com.co.kc.imchat.infrastructure.mybatis.entity.DbImChat;
import com.co.kc.imchat.infrastructure.mybatis.mapper.DbImChatMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 聊天表(DbImChat)表服务接口
 *
 * @author kc
 * @since 2026-02-03 11:11:22
 */
@Service
public class DbImChatService extends BaseMybatisService<DbImChatMapper, DbImChat> {

    public Optional<DbImChat> getByChatId(Long chatId) {
        return getFirst(getQueryWrapper().eq(DbImChat::getChatId, chatId));
    }

    public List<DbImChat> getListByChatIds(List<Long> chatIds) {
        if (CollectionUtils.isEmpty(chatIds)) {
            return Collections.emptyList();
        }
        return list(getQueryWrapper().in(DbImChat::getChatId, chatIds));
    }
}
