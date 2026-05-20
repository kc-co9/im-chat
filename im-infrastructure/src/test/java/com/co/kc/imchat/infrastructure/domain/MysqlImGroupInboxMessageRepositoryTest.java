package com.co.kc.imchat.infrastructure.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class MysqlImGroupInboxMessageRepositoryTest {

    @Test
    void findLastMessageListUsesGroupChatLastMessageId() throws IOException {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/co/kc/imchat/infrastructure/domain/MysqlImGroupInboxMessageRepository.java")),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("DbImGroupChat::getLastMessageId")
                .contains(".in(DbImGroupInboxMessage::getMessageId, lastMessageIds)");
    }
}
