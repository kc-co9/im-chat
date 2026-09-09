package com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DbIamApplicationAdministratorRoleMapperSqlTest {

    @Test
    void joinsAdministratorRoleByBusinessIdentifier() throws IOException {
        ClassPathResource resource = new ClassPathResource(
                "mapper/DbIamApplicationAdministratorRoleMapper.xml");
        try (InputStream inputStream = resource.getInputStream()) {
            String mapper = new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8);

            assertThat(mapper).contains(
                    "`administrator`.`administrator_id` = `administrator_role`.`administrator_id`");
            assertThat(mapper).doesNotContain(
                    "`administrator`.`id` = `administrator_role`.`administrator_id`");
        }
    }
}
