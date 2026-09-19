package com.co.kc.imchat.service.message.infrastructure.mybatis.service;

import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImGroupInboxMessage;
import com.co.kc.imchat.service.message.infrastructure.mybatis.mapper.DbImGroupInboxMessageMapper;
import com.co.kc.imchat.plugin.datasource.dao.BaseMybatisService;
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
import java.util.List;
import java.util.Optional;

@Service
public class DbImGroupInboxMessageService
        extends BaseMybatisService<DbImGroupInboxMessageMapper, DbImGroupInboxMessage> {
    private final SqlSessionFactory sqlSessionFactory;

    public DbImGroupInboxMessageService(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public boolean updateMessageStates(Collection<DbImGroupInboxMessage> messages) {
        // 同一 Spring 事务可能已绑定 SIMPLE session，MyBatis-Spring 禁止通过另一个
        // SqlSessionTemplate 切换 ExecutorType；沿用 MyBatis-Plus executeBatch 的方式打开
        // 临时 BATCH session，使其通过 SpringManagedTransaction 复用当前事务连接。
        try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
            DbImGroupInboxMessageMapper groupInboxMessageMapper =
                    sqlSession.getMapper(DbImGroupInboxMessageMapper.class);
            for (DbImGroupInboxMessage dbImGroupInboxMessage : messages) {
                groupInboxMessageMapper.update(this.getUpdateWrapper()
                        .set(DbImGroupInboxMessage::getStatus, dbImGroupInboxMessage.getStatus())
                        .set(DbImGroupInboxMessage::getReceiveTime, dbImGroupInboxMessage.getReceiveTime())
                        .set(DbImGroupInboxMessage::getReadTime, dbImGroupInboxMessage.getReadTime())
                        .set(DbImGroupInboxMessage::getRevokeTime, dbImGroupInboxMessage.getRevokeTime())
                        .setIncrBy(DbImGroupInboxMessage::getVersion, 1)
                        .eq(DbImGroupInboxMessage::getId, dbImGroupInboxMessage.getId())
                        .eq(DbImGroupInboxMessage::getUserId, dbImGroupInboxMessage.getUserId())
                        .eq(DbImGroupInboxMessage::getVersion, dbImGroupInboxMessage.getVersion())
                        .eq(DbImGroupInboxMessage::getIsDeleted, 0L));
            }
            int resultCount = 0;
            for (BatchResult result : sqlSession.flushStatements()) {
                for (int updateCount : result.getUpdateCounts()) {
                    if (updateCount == 0) {
                        throw new OptimisticLockingFailureException(
                                "Group inbox message was modified concurrently");
                    }
                    if (updateCount != 1) {
                        throw new DataAccessResourceFailureException(
                                "Unexpected group inbox message batch update count: " + updateCount);
                    }
                    resultCount++;
                }
            }
            if (resultCount != messages.size()) {
                throw new DataAccessResourceFailureException("Incomplete group inbox message batch update result");
            }
            return true;
        }
    }

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
