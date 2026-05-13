package com.co.kc.imchat.infrastructure.mybatis.service;

import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.mapper.DbImGroupChatMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class DbImGroupChatService extends BaseMybatisService<DbImGroupChatMapper, DbImGroupChat> {

    public Optional<DbImGroupChat> getByChatId(Long chatId) {
        return getFirst(this.getQueryWrapper().eq(DbImGroupChat::getChatId, chatId));
    }

    public List<DbImGroupChat> getByGroupId(Long groupId) {
        return list(getQueryWrapper().eq(DbImGroupChat::getGroupId, groupId));
    }

    public List<DbImGroupChat> getByGroupIds(List<Long> groupIds) {
        if (CollectionUtils.isEmpty(groupIds)) {
            return Collections.emptyList();
        }
        return list(getQueryWrapper().in(DbImGroupChat::getGroupId, groupIds));
    }

    public Optional<DbImGroupChat> getByGroupIdAndUserId(Long groupId, Long userId) {
        return getFirst(getQueryWrapper()
                .eq(DbImGroupChat::getGroupId, groupId)
                .eq(DbImGroupChat::getUserId, userId));
    }

    public List<DbImGroupChat> getListByUserId(Long userId) {
        return list(getQueryWrapper().eq(DbImGroupChat::getUserId, userId));
    }

    public List<DbImGroupChat> getListByUserIdAndGroupIds(Long userId, List<Long> groupIds) {
        if (CollectionUtils.isEmpty(groupIds)) {
            return Collections.emptyList();
        }
        return list(getQueryWrapper()
                .eq(DbImGroupChat::getUserId, userId)
                .in(DbImGroupChat::getGroupId, groupIds));
    }

    public List<DbImGroupChat> getListByUserIdAndChatIds(Long userId, List<Long> chatIds) {
        if (CollectionUtils.isEmpty(chatIds)) {
            return Collections.emptyList();
        }
        return list(getQueryWrapper()
                .eq(DbImGroupChat::getUserId, userId)
                .in(DbImGroupChat::getChatId, chatIds));
    }

    public List<DbImGroupChat> getListByGroupIdAndUserIds(Long groupId, List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyList();
        }
        return list(getQueryWrapper()
                .eq(DbImGroupChat::getGroupId, groupId)
                .in(DbImGroupChat::getUserId, userIds));
    }
}
