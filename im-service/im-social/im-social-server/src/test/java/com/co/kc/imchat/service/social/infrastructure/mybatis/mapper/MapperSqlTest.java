package com.co.kc.imchat.service.social.infrastructure.mybatis.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MapperSqlTest {

    @Test
    void groupSelectByUserIdFiltersDeletedGroupAndMemberRows() throws IOException {
        ClassPathResource resource = new ClassPathResource("mapper/DbImGroupMapper.xml");
        try (InputStream inputStream = resource.getInputStream()) {
            String mapper = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(mapper).contains("db_im_group.is_deleted = 0");
            assertThat(mapper).contains("db_im_group_member.is_deleted = 0");
        }
    }
}
