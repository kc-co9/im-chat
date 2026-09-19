package com.co.kc.imchat.service.message.infrastructure.domain;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.co.kc.imchat.common.domain.chat.model.ImChatId;
import com.co.kc.imchat.common.domain.group.model.GroupId;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupInboxMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupMessageStatus;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageContent;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageId;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageToken;
import com.co.kc.imchat.service.message.domain.message.model.ImMessageType;
import com.co.kc.imchat.service.message.infrastructure.domain.repository.MysqlImGroupInboxMessageRepository;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImGroupInboxMessage;
import com.co.kc.imchat.service.message.infrastructure.mybatis.service.DbImGroupInboxMessageService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.session.SqlSessionFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MysqlImGroupInboxMessageRepositoryTest {

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                DbImGroupInboxMessage.class);
    }

    @Test
    void saveNewMessagesUsesOneBatchWithoutChangingDomainPersistenceState() {
        RecordingGroupInboxMessageService service = new RecordingGroupInboxMessageService();
        MysqlImGroupInboxMessageRepository repository =
                new MysqlImGroupInboxMessageRepository(null, service);
        List<ImGroupInboxMessage> messages = List.of(message(101L, 1L), message(102L, 2L));

        repository.save(messages);

        assertThat(service.saved).hasSize(2);
        assertThat(messages)
                .extracting(ImGroupInboxMessage::getPkId)
                .containsOnlyNulls();
        assertThat(messages)
                .extracting(ImGroupInboxMessage::getRowVersion)
                .containsOnly(0L);
    }

    @Test
    void failedBatchInsertDoesNotAssignPersistenceIds() {
        RecordingGroupInboxMessageService service = new RecordingGroupInboxMessageService();
        service.batchResult = false;
        MysqlImGroupInboxMessageRepository repository =
                new MysqlImGroupInboxMessageRepository(null, service);
        List<ImGroupInboxMessage> messages = List.of(message(101L, 1L), message(102L, 2L));

        assertThatThrownBy(() -> repository.save(messages))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);

        assertThat(messages)
                .extracting(ImGroupInboxMessage::getPkId)
                .containsOnlyNulls();
    }

    @Test
    void saveMixedMessagesSeparatesBatchInsertFromExistingMessageUpdate() {
        RecordingGroupInboxMessageService service = new RecordingGroupInboxMessageService();
        MysqlImGroupInboxMessageRepository repository =
                new MysqlImGroupInboxMessageRepository(null, service);
        ImGroupInboxMessage newMessage = message(101L, 1L);
        ImGroupInboxMessage existingMessage = message(102L, 2L);
        existingMessage.setPkId(9L);
        existingMessage.setRowVersion(3L);

        repository.save(List.of(newMessage, existingMessage));

        assertThat(service.saved).hasSize(1);
        assertThat(service.saved.getFirst().getChatId()).isEqualTo(101L);
        assertThat(service.batchUpdated).hasSize(1);
        assertThat(service.batchUpdated.getFirst().getChatId()).isEqualTo(102L);
        assertThat(service.updated).isNull();
        assertThat(newMessage.getPkId()).isNull();
        assertThat(existingMessage.getRowVersion()).isEqualTo(3L);
    }

    @Test
    void saveExistingMessagesPropagatesBatchUpdateConflict() {
        RecordingGroupInboxMessageService service = new RecordingGroupInboxMessageService();
        service.batchUpdateFailure = true;
        MysqlImGroupInboxMessageRepository repository =
                new MysqlImGroupInboxMessageRepository(null, service);
        ImGroupInboxMessage existingMessage = message(102L, 2L);
        existingMessage.setPkId(9L);
        existingMessage.setRowVersion(3L);

        assertThatThrownBy(() -> repository.save(List.of(existingMessage)))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);

        assertThat(existingMessage.getRowVersion()).isEqualTo(3L);
    }

    @Test
    void findLastMessageListUsesGroupChatLastMessageId() throws IOException {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/co/kc/imchat/service/message/infrastructure/domain/repository/MysqlImGroupInboxMessageRepository.java")),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("DbImGroupChat::getLastMessageId")
                .contains(".in(DbImGroupInboxMessage::getMessageId, lastMessageIds)");
    }

    private static ImGroupInboxMessage message(Long chatId, Long userId) {
        return ImGroupInboxMessage.builder()
                .id(new ImMessageId(900L))
                .groupId(new GroupId(1001L))
                .chatId(new ImChatId(chatId))
                .userId(new UserId(userId))
                .token(new ImMessageToken("token-1"))
                .senderId(new UserId(1L))
                .content(new ImMessageContent(ImMessageType.TEXT, "hello"))
                .status(ImGroupMessageStatus.SENT)
                .sendTime(LocalDateTime.of(2026, 9, 15, 12, 0))
                .build();
    }

    private static class RecordingGroupInboxMessageService extends DbImGroupInboxMessageService {
        private List<DbImGroupInboxMessage> saved;
        private List<DbImGroupInboxMessage> batchUpdated;
        private DbImGroupInboxMessage updated;
        private boolean batchResult = true;
        private boolean batchUpdateFailure;

        private RecordingGroupInboxMessageService() {
            super(org.mockito.Mockito.mock(SqlSessionFactory.class));
        }

        @Override
        public boolean update(
                DbImGroupInboxMessage row,
                Wrapper<DbImGroupInboxMessage> updateWrapper) {
            updated = row;
            row.setVersion(row.getVersion() + 1L);
            return true;
        }

        @Override
        public boolean saveBatch(Collection<DbImGroupInboxMessage> rows, int batchSize) {
            saved = List.copyOf(rows);
            for (int index = 0; index < saved.size(); index++) {
                saved.get(index).setId((long) index + 1);
                saved.get(index).setVersion(0L);
            }
            return batchResult;
        }

        @Override
        public boolean updateMessageStates(Collection<DbImGroupInboxMessage> rows) {
            batchUpdated = List.copyOf(rows);
            if (batchUpdateFailure) {
                throw new org.springframework.dao.OptimisticLockingFailureException("batch update conflict");
            }
            batchUpdated.forEach(row -> row.setVersion(row.getVersion() + 1L));
            return true;
        }
    }
}
