package com.co.kc.imchat.infrastructure.mybatis.service;

import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChatSession;
import com.co.kc.imchat.infrastructure.mybatis.mapper.DbImGroupChatSessionMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class DbImGroupChatSessionService extends BaseMybatisService<DbImGroupChatSessionMapper, DbImGroupChatSession> {

    public Optional<DbImGroupChatSession> getByChatIdAndUserId(Long chatId, Long userId) {
        return getFirst(getQueryWrapper()
                .eq(DbImGroupChatSession::getChatId, chatId)
                .eq(DbImGroupChatSession::getUserId, userId));
    }

    public List<DbImGroupChatSession> listByChatIdsAndUserId(List<Long> chatIds, Long userId) {
        if (CollectionUtils.isEmpty(chatIds) || userId == null) {
            return Collections.emptyList();
        }
        return list(getQueryWrapper()
                .eq(DbImGroupChatSession::getUserId, userId)
                .in(DbImGroupChatSession::getChatId, chatIds));
    }

    public void upsertLastMessageId(Long chatId, Long userId, Long messageId) {
        if (Objects.isNull(chatId) || Objects.isNull(userId) || Objects.isNull(messageId)) {
            return;
        }
        Optional<DbImGroupChatSession> existing = getByChatIdAndUserId(chatId, userId);
        if (existing.isPresent()) {
            DbImGroupChatSession row = existing.get();
            row.setLastMessageId(messageId);
            updateById(row);
        } else {
            DbImGroupChatSession row = new DbImGroupChatSession();
            row.setChatId(chatId);
            row.setUserId(userId);
            row.setLastMessageId(messageId);
            row.setReadMessageId(0L);
            row.setUnreadMessageCount(0);
            save(row);
        }
    }
}
