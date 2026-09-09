package com.co.kc.imchat.service.message.infrastructure.mybatis.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class DdlSchemaTest {

    @Test
    void logicDeletedTablesUniqueKeysIncludeLogicDeleteColumn() throws IOException {
        String ddl = readDdl();

        assertThat(ddl).contains("UNIQUE KEY `uk_chat_id` (`chat_id`, `is_deleted`) USING BTREE");
        assertThat(ddl).contains("UNIQUE KEY `uk_user_peer` (`user_id`, `peer_user_id`, `is_deleted`) USING BTREE");
        assertThat(ddl).contains("UNIQUE KEY `uk_group_user` (`group_id`, `user_id`, `is_deleted`) USING BTREE");
        assertThat(ddl).contains("UNIQUE KEY `uk_chat_token` (`chat_id`, `token`, `is_deleted`) USING BTREE");
        assertThat(ddl).contains("UNIQUE KEY `uk_chat_message` (`chat_id`, `message_id`, `is_deleted`) USING BTREE");
        assertThat(ddl).contains("UNIQUE KEY `uk_chat_user_token` (`chat_id`, `user_id`, `token`, `is_deleted`) USING BTREE");
        assertThat(ddl).contains("UNIQUE KEY `uk_chat_user_message` (`chat_id`, `user_id`, `message_id`, `is_deleted`) USING BTREE");
    }

    @Test
    void ownsOnlyTheMessageSchemaAndConversationTables() throws IOException {
        String ddl = readDdl();

        assertThat(ddl)
                .contains("CREATE DATABASE IF NOT EXISTS `im_chat_message`")
                .contains("USE `im_chat_message`")
                .contains("CREATE TABLE `db_im_private_chat`")
                .contains("CREATE TABLE `db_im_group_chat`")
                .contains("CREATE TABLE `db_im_private_inbox_message`")
                .contains("CREATE TABLE `db_im_group_inbox_message`")
                .doesNotContain("`db_user`", "`db_friend`", "CREATE TABLE `db_im_group`");
    }

    @Test
    void groupChatTableDoesNotNeedReadTimeColumn() throws IOException {
        String ddl = readDdl();
        String groupChatDdl = ddl.substring(
                ddl.indexOf("CREATE TABLE `db_im_group_chat`"),
                ddl.indexOf("DROP TABLE IF EXISTS `db_im_private_inbox_message`"));

        assertThat(groupChatDdl).doesNotContain("`read_time`");
    }

    private String readDdl() throws IOException {
        return Files.readString(Path.of("sql/ddl.sql"), StandardCharsets.UTF_8);
    }
}
