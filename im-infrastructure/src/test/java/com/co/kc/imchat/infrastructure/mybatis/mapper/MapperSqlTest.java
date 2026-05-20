package com.co.kc.imchat.infrastructure.mybatis.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class MapperSqlTest {

    @Test
    void groupSelectByUserIdFiltersDeletedGroupAndMemberRows() throws IOException {
        String mapper = new String(Files.readAllBytes(
                Paths.get("src/main/resources/mapper/DbImGroupMapper.xml")), StandardCharsets.UTF_8);

        assertThat(mapper).contains("db_im_group.is_deleted = 0");
        assertThat(mapper).contains("db_im_group_member.is_deleted = 0");
    }
}
