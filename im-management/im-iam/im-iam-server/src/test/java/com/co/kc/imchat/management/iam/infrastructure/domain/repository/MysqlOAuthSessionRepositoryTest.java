package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationId;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamOAuthAuthorization;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamAdministratorService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamOAuthAuthorizationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlOAuthSessionRepositoryTest {

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                DbIamOAuthAuthorization.class);
    }

    @Test
    void bulkRevokeInvalidatesPreviouslyLoadedVersions() {
        DbIamOAuthAuthorizationService authorizationService = mock(DbIamOAuthAuthorizationService.class);
        when(authorizationService.getUpdateWrapper()).thenReturn(new LambdaUpdateWrapper<>());
        when(authorizationService.update(any(Wrapper.class))).thenReturn(true);
        MysqlOAuthSessionRepository repository = new MysqlOAuthSessionRepository(
                authorizationService,
                mock(DbIamAdministratorService.class));

        boolean revoked = repository.revoke(
                new OAuthAuthorizationId("authorization-1"),
                Instant.parse("2026-09-12T08:00:00Z"));

        assertThat(revoked).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<DbIamOAuthAuthorization>> wrapperCaptor =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(authorizationService).update(wrapperCaptor.capture());
        LambdaUpdateWrapper<DbIamOAuthAuthorization> update =
                (LambdaUpdateWrapper<DbIamOAuthAuthorization>) wrapperCaptor.getValue();
        assertThat(update.getSqlSet())
                .contains("status")
                .contains("revoked_at")
                .contains("version=version +");
        assertThat(update.getSqlSegment())
                .contains("authorization_id")
                .contains("status");
    }
}
