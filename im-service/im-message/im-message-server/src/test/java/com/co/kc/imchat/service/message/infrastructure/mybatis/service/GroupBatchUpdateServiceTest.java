package com.co.kc.imchat.service.message.infrastructure.mybatis.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImGroupInboxMessage;
import com.co.kc.imchat.service.message.infrastructure.mybatis.mapper.DbImGroupChatMapper;
import com.co.kc.imchat.service.message.infrastructure.mybatis.mapper.DbImGroupInboxMessageMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupBatchUpdateServiceTest {

    @BeforeAll
    static void initializeTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DbImGroupChat.class);
        TableInfoHelper.initTableInfo(assistant, DbImGroupInboxMessage.class);
    }

    @Test
    void groupChatBatchUpdateUsesScopedLambdaUpdateAndAcceptsOneAffectedRow() {
        SqlSessionFactory sqlSessionFactory = mock(SqlSessionFactory.class);
        SqlSession sqlSession = mock(SqlSession.class);
        DbImGroupChatMapper mapper = mock(DbImGroupChatMapper.class);
        DbImGroupChat first = groupChat(1L, 11L, 3L);
        DbImGroupChat second = groupChat(2L, 12L, 4L);
        when(sqlSessionFactory.openSession(ExecutorType.BATCH)).thenReturn(sqlSession);
        when(sqlSession.getMapper(DbImGroupChatMapper.class)).thenReturn(mapper);
        when(sqlSession.flushStatements()).thenReturn(List.of(batchResult(1, 1)));
        DbImGroupChatService service = new DbImGroupChatService(sqlSessionFactory);

        boolean updated = service.updateConversationStates(List.of(first, second));

        assertThat(updated).isTrue();
        ArgumentCaptor<Wrapper<DbImGroupChat>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper, times(2)).update(wrapperCaptor.capture());
        LambdaUpdateWrapper<DbImGroupChat> updateWrapper =
                (LambdaUpdateWrapper<DbImGroupChat>) wrapperCaptor.getAllValues().getFirst();
        assertThat(updateWrapper.getSqlSet())
                .contains(
                        "last_message_id",
                        "read_message_id",
                        "unread_message_count",
                        "status",
                        "active_time",
                        "version")
                .doesNotContain("chat_id", "group_id", "user_id", "group_alias");
        assertThat(updateWrapper.getSqlSegment())
                .contains("id", "user_id", "version", "is_deleted");
    }

    @Test
    void groupChatBatchUpdateRejectsOptimisticLockConflict() {
        SqlSessionFactory sqlSessionFactory = mock(SqlSessionFactory.class);
        SqlSession sqlSession = mock(SqlSession.class);
        DbImGroupChatMapper mapper = mock(DbImGroupChatMapper.class);
        when(sqlSessionFactory.openSession(ExecutorType.BATCH)).thenReturn(sqlSession);
        when(sqlSession.getMapper(DbImGroupChatMapper.class)).thenReturn(mapper);
        when(sqlSession.flushStatements()).thenReturn(List.of(batchResult(1, 0)));
        DbImGroupChatService service = new DbImGroupChatService(sqlSessionFactory);

        assertThatThrownBy(() -> service.updateConversationStates(List.of(
                groupChat(1L, 11L, 3L),
                groupChat(2L, 12L, 4L))))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void groupInboxBatchUpdateRejectsUnknownAffectedRowCount() {
        SqlSessionFactory sqlSessionFactory = mock(SqlSessionFactory.class);
        SqlSession sqlSession = mock(SqlSession.class);
        DbImGroupInboxMessageMapper mapper = mock(DbImGroupInboxMessageMapper.class);
        DbImGroupInboxMessage message = groupInboxMessage(1L, 11L, 3L);
        when(sqlSessionFactory.openSession(ExecutorType.BATCH)).thenReturn(sqlSession);
        when(sqlSession.getMapper(DbImGroupInboxMessageMapper.class)).thenReturn(mapper);
        when(sqlSession.flushStatements())
                .thenReturn(List.of(batchResult(Statement.SUCCESS_NO_INFO)));
        DbImGroupInboxMessageService service = new DbImGroupInboxMessageService(sqlSessionFactory);

        assertThatThrownBy(() -> service.updateMessageStates(List.of(message)))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(mapper).update(any(Wrapper.class));
    }

    private BatchResult batchResult(int... updateCounts) {
        BatchResult result = new BatchResult(null, "");
        result.setUpdateCounts(updateCounts);
        return result;
    }

    private DbImGroupChat groupChat(Long id, Long userId, Long version) {
        DbImGroupChat groupChat = new DbImGroupChat();
        groupChat.setId(id);
        groupChat.setUserId(userId);
        groupChat.setVersion(version);
        return groupChat;
    }

    private DbImGroupInboxMessage groupInboxMessage(Long id, Long userId, Long version) {
        DbImGroupInboxMessage message = new DbImGroupInboxMessage();
        message.setId(id);
        message.setUserId(userId);
        message.setVersion(version);
        return message;
    }
}
