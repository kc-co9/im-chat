package com.co.kc.imchat.infrastructure.domain;

import com.co.kc.imchat.domain.chat.ImChatId;
import com.co.kc.imchat.domain.chat.ImChatType;
import com.co.kc.imchat.domain.chat.ImGroupChat;
import com.co.kc.imchat.domain.group.ImGroupId;
import com.co.kc.imchat.domain.user.UserId;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import com.co.kc.imchat.infrastructure.mybatis.service.DbImGroupChatService;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class MysqlImGroupChatRepositoryTest {

    @Test
    void saveAllUpsertsExistingGroupChats() {
        RecordingGroupChatService groupChatService = new RecordingGroupChatService();
        MysqlImGroupChatRepository repository = new MysqlImGroupChatRepository(groupChatService, null);
        ImGroupChat groupChat = ImGroupChat.builder()
                .pkId(1L)
                .id(new ImChatId(101L))
                .groupId(new ImGroupId(1001L))
                .userId(new UserId(1L))
                .type(ImChatType.GROUP)
                .unreadMessageCount(1)
                .build();

        repository.saveAll(Collections.singletonList(groupChat));

        assertThat(groupChatService.saveOrUpdateBatchCalled).isTrue();
        assertThat(groupChatService.saveBatchCalled).isFalse();
    }

    private static class RecordingGroupChatService extends DbImGroupChatService {
        private boolean saveBatchCalled;
        private boolean saveOrUpdateBatchCalled;

        @Override
        public boolean saveBatch(Collection<DbImGroupChat> entityList) {
            saveBatchCalled = true;
            return true;
        }

        @Override
        public boolean saveOrUpdateBatch(Collection<DbImGroupChat> entityList) {
            saveOrUpdateBatchCalled = true;
            return true;
        }
    }
}
