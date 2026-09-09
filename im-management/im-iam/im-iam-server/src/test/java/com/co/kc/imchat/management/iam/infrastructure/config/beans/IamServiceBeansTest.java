package com.co.kc.imchat.management.iam.infrastructure.config.beans;

import com.co.kc.imchat.management.iam.application.AdministratorAppService;
import com.co.kc.imchat.management.iam.application.AdministratorAuthenticationAppService;
import com.co.kc.imchat.management.audit.sdk.client.AuditClient;
import com.co.kc.imchat.management.audit.sdk.context.AuditContextCollector;
import com.co.kc.imchat.management.audit.sdk.support.AuditEventFactory;
import com.co.kc.imchat.management.audit.sdk.support.AuditFailureReporter;
import com.co.kc.imchat.management.audit.sdk.support.AuditTemplate;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.domain.administrator.service.PasswordService;
import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.plugin.identity.snowflake.impl.StaticSnowflakeMachineId;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamAdministratorMapper;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamApplicationAdministratorRoleMapper;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamAppMapper;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamOAuthAuthorizationMapper;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamApplicationPermissionMapper;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamApplicationRoleMapper;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamApplicationRolePermissionMapper;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamOAuthClientMapper;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamInternalAdministratorRoleMapper;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamInternalRoleMapper;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamAdministratorService;
import com.co.kc.imchat.management.iam.support.restriction.AuthenticationRestriction;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IamServiceBeansTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    IamRepositoryBeans.class,
                    IamServiceBeans.class,
                    TestDependencies.class);

    @Test
    void assemblesAdministratorAuthenticationComponentsFromTypedProperties() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AdministratorRepository.class);
            assertThat(context).hasSingleBean(PasswordService.class);
            assertThat(context).hasSingleBean(AuthenticationRestriction.class);
            assertThat(context).hasSingleBean(AdministratorAuthenticationAppService.class);
            assertThat(context).hasSingleBean(AdministratorAppService.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = DbIamAdministratorService.class)
    static class TestDependencies {
        @Bean
        SnowflakeId snowflakeId() {
            return new SnowflakeId(new StaticSnowflakeMachineId(7L, 9L));
        }

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        DbIamAdministratorMapper administratorMapper() {
            return mock(DbIamAdministratorMapper.class);
        }

        @Bean
        DbIamApplicationAdministratorRoleMapper administratorRoleMapper() {
            return mock(DbIamApplicationAdministratorRoleMapper.class);
        }

        @Bean
        DbIamAppMapper applicationMapper() {
            return mock(DbIamAppMapper.class);
        }

        @Bean
        DbIamOAuthClientMapper oauthClientMapper() {
            return mock(DbIamOAuthClientMapper.class);
        }

        @Bean
        DbIamOAuthAuthorizationMapper authorizationMapper() {
            return mock(DbIamOAuthAuthorizationMapper.class);
        }

        @Bean
        DbIamApplicationPermissionMapper permissionMapper() {
            return mock(DbIamApplicationPermissionMapper.class);
        }

        @Bean
        DbIamApplicationRoleMapper roleMapper() {
            return mock(DbIamApplicationRoleMapper.class);
        }

        @Bean
        DbIamApplicationRolePermissionMapper rolePermissionMapper() {
            return mock(DbIamApplicationRolePermissionMapper.class);
        }

        @Bean
        DbIamInternalAdministratorRoleMapper internalAdministratorRoleMapper() {
            return mock(DbIamInternalAdministratorRoleMapper.class);
        }

        @Bean
        DbIamInternalRoleMapper internalRoleMapper() {
            return mock(DbIamInternalRoleMapper.class);
        }

        @Bean
        AuditClient auditClient() {
            return mock(AuditClient.class);
        }

        @Bean
        AuditContextCollector auditContextCollector() {
            return mock(AuditContextCollector.class);
        }

        @Bean
        AuditEventFactory auditEventFactory() {
            return mock(AuditEventFactory.class);
        }

        @Bean
        AuditFailureReporter auditFailureReporter() {
            return mock(AuditFailureReporter.class);
        }

        @Bean
        AuditTemplate auditTemplate() {
            return mock(AuditTemplate.class);
        }
    }
}
