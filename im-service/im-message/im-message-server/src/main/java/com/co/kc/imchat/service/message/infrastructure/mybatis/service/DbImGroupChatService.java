package com.co.kc.imchat.service.message.infrastructure.mybatis.service;

import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.service.message.infrastructure.mybatis.mapper.DbImGroupChatMapper;
import com.co.kc.imchat.plugin.datasource.dao.BaseMybatisService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class DbImGroupChatService extends BaseMybatisService<DbImGroupChatMapper, DbImGroupChat> {
    private final SqlSessionFactory sqlSessionFactory;

    public DbImGroupChatService(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public boolean updateConversationStates(Collection<DbImGroupChat> dbImGroupChats) {
        // 同一 Spring 事务可能已绑定 SIMPLE session，MyBatis-Spring 禁止通过另一个
        // SqlSessionTemplate 切换 ExecutorType；沿用 MyBatis-Plus executeBatch 的方式打开
        // 临时 BATCH session，使其通过 SpringManagedTransaction 复用当前事务连接。
        try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
            DbImGroupChatMapper groupChatMapper = sqlSession.getMapper(DbImGroupChatMapper.class);
            for (DbImGroupChat dbImGroupChat : dbImGroupChats) {
                groupChatMapper.update(this.getUpdateWrapper()
                        .set(DbImGroupChat::getLastMessageId, dbImGroupChat.getLastMessageId())
                        .set(DbImGroupChat::getReadMessageId, dbImGroupChat.getReadMessageId())
                        .set(DbImGroupChat::getUnreadMessageCount, dbImGroupChat.getUnreadMessageCount())
                        .set(DbImGroupChat::getStatus, dbImGroupChat.getStatus())
                        .set(DbImGroupChat::getActiveTime, dbImGroupChat.getActiveTime())
                        .setIncrBy(DbImGroupChat::getVersion, 1)
                        .eq(DbImGroupChat::getId, dbImGroupChat.getId())
                        .eq(DbImGroupChat::getUserId, dbImGroupChat.getUserId())
                        .eq(DbImGroupChat::getVersion, dbImGroupChat.getVersion())
                        .eq(DbImGroupChat::getIsDeleted, 0L));
            }
            int resultCount = 0;
            for (BatchResult result : sqlSession.flushStatements()) {
                for (int updateCount : result.getUpdateCounts()) {
                    if (updateCount == 0) {
                        throw new OptimisticLockingFailureException(
                                "Group chat was modified concurrently");
                    }
                    if (updateCount != 1) {
                        throw new DataAccessResourceFailureException(
                                "Unexpected group chat batch update count: " + updateCount);
                    }
                    resultCount++;
                }
            }
            if (resultCount != dbImGroupChats.size()) {
                throw new DataAccessResourceFailureException("Incomplete group chat batch update result");
            }
            return true;
        }
    }

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

    public void removeByGroupIdAndUserId(Long groupId, Long userId) {
        remove(getQueryWrapper()
                .eq(DbImGroupChat::getGroupId, groupId)
                .eq(DbImGroupChat::getUserId, userId));
    }
}
