package com.co.kc.imchat.service.account.infrastructure.mybatis;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AccountDdlSchemaTest {

    @Test
    void ownsOnlyTheAccountSchemaAndUserTable() throws IOException {
        String ddl = Files.readString(Path.of("sql/ddl.sql"));

        assertThat(ddl)
                .contains("CREATE DATABASE IF NOT EXISTS `im_chat_account`")
                .contains("USE `im_chat_account`")
                .contains("CREATE TABLE `db_user`")
                .doesNotContain("`db_friend`", "`db_im_group`", "`db_im_private_chat`");
    }
}
