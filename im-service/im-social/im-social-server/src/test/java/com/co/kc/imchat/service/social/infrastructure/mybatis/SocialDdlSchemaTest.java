package com.co.kc.imchat.service.social.infrastructure.mybatis;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SocialDdlSchemaTest {

    @Test
    void ownsOnlyTheSocialSchemaAndRelationshipTables() throws IOException {
        String ddl = Files.readString(Path.of("sql/ddl.sql"));

        assertThat(ddl)
                .contains("CREATE DATABASE IF NOT EXISTS `im_chat_social`")
                .contains("USE `im_chat_social`")
                .contains("CREATE TABLE `db_friend`")
                .contains("CREATE TABLE `db_im_group`")
                .contains("CREATE TABLE `db_im_group_member`")
                .doesNotContain("`db_user`", "`db_im_private_chat`", "`db_im_group_chat`");
    }
}
