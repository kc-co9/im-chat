package com.co.kc.imchat.service.account.transformer.domain;

import com.co.kc.imchat.service.account.domain.user.model.ManagedUser;
import com.co.kc.imchat.service.account.domain.user.model.UserStatus;
import com.co.kc.imchat.service.account.infrastructure.mybatis.entity.DbUser;
import com.co.kc.imchat.service.account.infrastructure.mybatis.enums.DbUserStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedUserDomainTransformerTest {

    @Test
    void mapsPersistenceUserAndDeletionFactToDomainSnapshot() {
        DbUser source = new DbUser();
        source.setId(9L);
        source.setUserId(1001L);
        source.setUsername("alice");
        source.setEmail("alice@example.com");
        source.setPassword("encrypted-password");
        source.setStatus(DbUserStatus.BANNED);
        source.setIsDeleted(1001L);
        source.setCreateTime(Instant.parse("2026-08-24T01:00:00Z"));
        source.setUpdateTime(Instant.parse("2026-08-25T02:00:00Z"));

        ManagedUser result = ManagedUserDomainTransformer.INSTANCE
                .managedUserFrom(source);

        assertThat(result.getPkId()).isEqualTo(9L);
        assertThat(result.getUserId().value()).isEqualTo(1001L);
        assertThat(result.getUsername().value()).isEqualTo("alice");
        assertThat(result.getEmail().value()).isEqualTo("alice@example.com");
        assertThat(result.getPassword().value()).isEqualTo("encrypted-password");
        assertThat(result.getStatus()).isEqualTo(UserStatus.BANNED);
        assertThat(result.getDeleted()).isTrue();
        assertThat(result.getCreatedAt()).isEqualTo(Instant.parse("2026-08-24T01:00:00Z"));
        assertThat(result.getUpdatedAt()).isEqualTo(Instant.parse("2026-08-25T02:00:00Z"));
    }
}
