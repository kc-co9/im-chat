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

import java.util.ArrayDeque;
import java.util.Arrays;
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
    void saveAllUsesVersionedPerShardUpdates() {
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

        assertThat(groupChatService.updated).isNotNull();
        assertThat(groupChatService.updateWrapper.getSqlSegment())
                .contains("id")
                .contains("user_id");
        assertThat(groupChat.getRowVersion()).isEqualTo(1L);
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

        private RecordingGroupChatService(Boolean... updateResults) {
            this.updateResults = new ArrayDeque<>(Arrays.asList(updateResults));
        }

        @Override
        public boolean update(DbImGroupChat entity, Wrapper<DbImGroupChat> wrapper) {
            updated = entity;
            updateWrapper = wrapper;
            entity.setVersion(entity.getVersion() + 1);
            return updateResults.remove();
        }
    }
}
