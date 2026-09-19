package com.co.kc.imchat.service.message.infrastructure.domain;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.service.message.domain.chat.model.ImChatType;
import com.co.kc.imchat.service.message.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.message.infrastructure.domain.repository.MysqlImGroupChatRepository;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.service.message.infrastructure.mybatis.service.DbImGroupChatService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MysqlImGroupChatRepositoryTest {

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                DbImGroupChat.class);
    }

    @Test
    void saveAllDelegatesVersionedBatchUpdateWithoutQueryingStoredVersions() {
        RecordingGroupChatService groupChatService = new RecordingGroupChatService(true);
        MysqlImGroupChatRepository repository = new MysqlImGroupChatRepository(groupChatService, null);
        ImGroupChat groupChat = ImGroupChat.builder()
                .id(new ImChatId(101L))
                .groupId(new GroupId(1001L))
                .userId(new UserId(1L))
                .type(ImChatType.GROUP)
                .unreadMessageCount(1)
                .build();
        groupChat.setPkId(1L);
        repository.save(Collections.singletonList(groupChat));

        assertThat(groupChatService.batchUpdated).hasSize(1);
        assertThat(groupChatService.updated).isNull();
        assertThat(groupChat.getRowVersion()).isZero();
    }

    @Test
    void saveAllRejectsBatchWhenStoredVersionDidNotAdvance() {
        RecordingGroupChatService groupChatService = new RecordingGroupChatService(true);
        MysqlImGroupChatRepository repository = new MysqlImGroupChatRepository(groupChatService, null);
        ImGroupChat groupChat = ImGroupChat.builder()
                .id(new ImChatId(101L))
                .groupId(new GroupId(1001L))
                .userId(new UserId(1L))
                .type(ImChatType.GROUP)
                .unreadMessageCount(1)
                .build();
        groupChat.setPkId(1L);
        groupChatService.batchUpdateFailure = true;

        assertThatThrownBy(() -> repository.save(Collections.singletonList(groupChat)))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);

        assertThat(groupChat.getRowVersion()).isZero();
    }

    @Test
    void saveAllDoesNotAdvanceAnyDomainStateWhenLaterGroupChatConflicts() {
        RecordingGroupChatService groupChatService = new RecordingGroupChatService(true);
        MysqlImGroupChatRepository repository = new MysqlImGroupChatRepository(groupChatService, null);
        ImGroupChat first = groupChat(101L, 1L, 1L);
        ImGroupChat second = groupChat(102L, 2L, 2L);
        groupChatService.batchUpdateFailure = true;

        assertThatThrownBy(() -> repository.save(List.of(first, second)))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);

        assertThat(first.getRowVersion()).isZero();
        assertThat(second.getRowVersion()).isZero();
    }

    @Test
    void saveAllDoesNotBatchUpdateNewlyInsertedChats() {
        RecordingGroupChatService service = new RecordingGroupChatService(true);
        MysqlImGroupChatRepository repository = new MysqlImGroupChatRepository(service, null);
        ImGroupChat newChat = ImGroupChat.builder()
                .id(new ImChatId(101L))
                .groupId(new GroupId(1001L))
                .userId(new UserId(1L))
                .type(ImChatType.GROUP)
                .unreadMessageCount(0)
                .build();

        repository.save(List.of(newChat));

        assertThat(service.batchInserted).hasSize(1);
        assertThat(service.batchUpdated).isNull();
        assertThat(newChat.getPkId()).isNull();
        assertThat(newChat.getRowVersion()).isZero();
    }

    @Test
    void failedUpdateDoesNotAdvanceDomainRowVersion() {
        RecordingGroupChatService groupChatService = new RecordingGroupChatService(false);
        MysqlImGroupChatRepository repository = new MysqlImGroupChatRepository(groupChatService, null);
        ImGroupChat groupChat = ImGroupChat.builder()
                .id(new ImChatId(101L))
                .groupId(new GroupId(1001L))
                .userId(new UserId(1L))
                .type(ImChatType.GROUP)
                .unreadMessageCount(1)
                .build();
        groupChat.setPkId(1L);

        assertThatThrownBy(() -> repository.save(groupChat))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);

        assertThat(groupChat.getRowVersion()).isZero();
    }

    private static class RecordingGroupChatService extends DbImGroupChatService {
        private final Queue<Boolean> updateResults;
        private DbImGroupChat updated;
        private Wrapper<DbImGroupChat> updateWrapper;
        private List<DbImGroupChat> batchUpdated;
        private List<DbImGroupChat> batchInserted;
        private boolean batchUpdateFailure;

        private RecordingGroupChatService(Boolean... updateResults) {
            super(org.mockito.Mockito.mock(SqlSessionFactory.class));
            this.updateResults = new ArrayDeque<>(Arrays.asList(updateResults));
        }

        @Override
        public boolean update(DbImGroupChat entity, Wrapper<DbImGroupChat> wrapper) {
            updated = entity;
            updateWrapper = wrapper;
            entity.setVersion(entity.getVersion() + 1);
            return updateResults.remove();
        }

        @Override
        public boolean updateConversationStates(Collection<DbImGroupChat> dbImGroupChats) {
            batchUpdated = List.copyOf(dbImGroupChats);
            if (batchUpdateFailure) {
                throw new org.springframework.dao.OptimisticLockingFailureException("batch update conflict");
            }
            batchUpdated.forEach(row -> row.setVersion(row.getVersion() + 1L));
            return true;
        }

        @Override
        public boolean saveBatch(Collection<DbImGroupChat> rows, int batchSize) {
            batchInserted = List.copyOf(rows);
            batchInserted.getFirst().setId(1L);
            return true;
        }

        @Override
        public List<DbImGroupChat> getListByGroupIdAndUserIds(Long groupId, List<Long> userIds) {
            throw new AssertionError("Repository must not verify MyBatis batch results");
        }
    }

    private static ImGroupChat groupChat(Long chatId, Long userId, Long pkId) {
        ImGroupChat chat = ImGroupChat.builder()
                .id(new ImChatId(chatId))
                .groupId(new GroupId(1001L))
                .userId(new UserId(userId))
                .type(ImChatType.GROUP)
                .unreadMessageCount(1)
                .build();
        chat.setPkId(pkId);
        return chat;
    }
}
