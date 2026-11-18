package com.co.kc.imchat.management.iam.application;

import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlOAuthAuthorizationRepository;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlOAuthSessionRepository;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlOAuthClientRepository;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamOAuthClientService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamAdministratorService;
import com.co.kc.imchat.management.iam.domain.application.repository.ApplicationRepository;
import com.co.kc.imchat.management.iam.domain.administrator.repository.AdministratorRepository;
import com.co.kc.imchat.management.iam.domain.application.service.OAuthClientSecretService;
import com.co.kc.imchat.management.iam.domain.authorization.service.AdministratorAuthorizationService;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthClientDisableCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthClientAccessUpdateCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.AdministratorDisableCmd;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.service.AdministratorService;
import com.co.kc.imchat.management.iam.domain.administrator.service.PasswordService;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamRoleRepository;
import com.co.kc.imchat.management.iam.domain.authorization.repository.IamAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlAdministratorRepository;
import com.co.kc.imchat.management.iam.support.restriction.AuthenticationRestriction;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationPermissionCatalogSyncCmd;
import com.co.kc.imchat.management.iam.domain.authorization.service.ApplicationPermissionService;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlApplicationPermissionRepository;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlApplicationRepository;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationPermissionService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationRolePermissionService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamAppService;
import com.co.kc.imchat.management.iam.transformer.domain.ApplicationPermissionDomainTransformer;
import com.co.kc.imchat.plugin.identity.snowflake.SnowflakeId;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlApplicationRoleRepository;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlApplicationAdministratorRoleRepository;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationRoleService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamApplicationAdministratorRoleService;
import com.co.kc.imchat.management.iam.transformer.domain.ApplicationRoleDomainTransformer;
import com.co.kc.imchat.management.iam.domain.authorization.service.ApplicationAdministratorRoleService;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationRoleUpdateCmd;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationRoleAssignmentChangeCmd;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.mockito.Mockito.mock;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClient;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientType;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientName;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientStatus;
import com.co.kc.imchat.management.iam.domain.application.model.RedirectUri;
import java.net.URI;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthScope;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorization;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationId;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationStatus;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthPrincipal;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthPrincipalType;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamOAuthAuthorizationMapper;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamOAuthAuthorizationService;
import com.co.kc.imchat.plugin.datasource.ImDatasourceAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 必须连接任务专属真实 MySQL；H2 与 mock 不提供本测试的并发证据。 */
@EnabledIfSystemProperty(named = "im.test.local.mysql", matches = "true")
@SpringJUnitConfig
@Import({DbIamOAuthAuthorizationService.class, MysqlOAuthAuthorizationRepository.class,
        DbIamOAuthClientService.class, DbIamAdministratorService.class, MysqlOAuthSessionRepository.class,
        MysqlOAuthClientRepository.class, MysqlApplicationRepository.class,
        DbIamAppService.class, DbIamApplicationPermissionService.class,
        DbIamApplicationRolePermissionService.class, DbIamApplicationRoleService.class,
        DbIamApplicationAdministratorRoleService.class})
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
        ImDatasourceAutoConfiguration.class})
@MapperScan(basePackageClasses = DbIamOAuthAuthorizationMapper.class)
@TestPropertySource(properties = {
        "spring.datasource.url=${im.test.mysql.url}", "spring.datasource.username=root",
        "spring.datasource.password=", "spring.sql.init.mode=never"})
class IamAuthorizationInvalidationTransactionTest {
    @Autowired private MysqlOAuthAuthorizationRepository repository;
    @Autowired private DbIamOAuthAuthorizationService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Autowired private MysqlOAuthClientRepository clients;
    @Autowired private MysqlOAuthSessionRepository sessions;

    @Test
    void publicClientRoundTripsThroughActualMysqlWithoutSecret() {
        String clientId = "public-" + UUID.randomUUID();
        OAuthClient client = OAuthClient.builder().clientType(OAuthClientType.PUBLIC)
                .clientId(new OAuthClientId(clientId)).appId(new AppId(10004L)).audienceAppId(new AppId(10004L))
                .name(new OAuthClientName("Ops CLI"))
                .grantTypes(Set.of(OAuthGrantType.AUTHORIZATION_CODE))
                .scopes(Set.of(new OAuthScope("ops:broker:read")))
                .redirectUris(Set.of(new RedirectUri(URI.create("http://127.0.0.1:8765/oauth/callback"))))
                .postLogoutRedirectUris(Set.of()).status(OAuthClientStatus.ACTIVE).build();
        clients.save(client);
        OAuthClient restored = clients.find(new OAuthClientId(clientId)).orElseThrow();
        assertThat(restored.getClientType()).isEqualTo(OAuthClientType.PUBLIC);
        assertThat(restored.getClientSecret()).isNull();
        assertThat(restored.getRedirectUris()).isEqualTo(client.getRedirectUris());
        assertThat(jdbc.queryForObject("SELECT client_secret_hash FROM db_iam_oauth_client WHERE oauth_client_id=?",
                String.class, clientId)).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void clientChangeCommitsWithAuthorizationInvalidation(boolean reviseAccess) {
        String clientId = "tx-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO db_iam_oauth_client(oauth_client_id,app_id,audience_app_id,name,client_type,
                client_secret_hash,grant_types,scopes,redirect_uris,post_logout_redirect_uris,status)
                VALUES (?,10004,10004,'transaction client','CONFIDENTIAL','digest','CLIENT_CREDENTIALS',
                'ops:broker:read','[]','[]',1)
                """, clientId);
        OAuthAuthorization authorization = authorization(clientId);
        repository.save(authorization);
        OAuthClientAppService target = new OAuthClientAppService(mock(ApplicationRepository.class), clients,
                mock(OAuthClientSecretService.class), mock(AdministratorRepository.class),
                mock(AdministratorAuthorizationService.class), sessions);
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        OAuthClientAppService app = (OAuthClientAppService) factory.getProxy();
        Runnable change = reviseAccess
                ? () -> app.updateAccess(new OAuthClientAccessUpdateCmd(clientId, Set.of("ops:broker:raft:write"),
                        Set.of(), Set.of()))
                : () -> app.disable(new OAuthClientDisableCmd(clientId));
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            change.run();
            throw new IllegalStateException("rollback client change");
        })).isInstanceOf(IllegalStateException.class).hasMessage("rollback client change");
        assertThat(clients.find(new OAuthClientId(clientId)).orElseThrow().isActive()).isTrue();
        assertThat(clients.find(new OAuthClientId(clientId)).orElseThrow().getScopes())
                .containsExactly(new OAuthScope("ops:broker:read"));
        assertThat(repository.find(authorization.getId()).orElseThrow().isRevoked()).isFalse();
        change.run();
        OAuthClient changed = clients.find(new OAuthClientId(clientId)).orElseThrow();
        assertThat(changed.isActive()).isEqualTo(reviseAccess);
        if (reviseAccess) {
            assertThat(changed.getScopes()).containsExactly(new OAuthScope("ops:broker:raft:write"));
        }
        assertThat(repository.find(authorization.getId()).orElseThrow().isRevoked()).isTrue();
    }

    @Test
    void failedRevocationSqlRollsBackClientDisable() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String clientId = "failure-" + suffix;
        String trigger = "t7_revoke_failure_" + suffix;
        jdbc.update("""
                INSERT INTO db_iam_oauth_client(oauth_client_id,app_id,audience_app_id,name,client_type,
                client_secret_hash,grant_types,scopes,redirect_uris,post_logout_redirect_uris,status)
                VALUES (?,10004,10004,'Failure test','CONFIDENTIAL','digest','CLIENT_CREDENTIALS',
                'ops:broker:read','[]','[]',1)
                """, clientId);
        OAuthAuthorization authorization = authorization(clientId);
        repository.save(authorization);
        OAuthClientAppService target = new OAuthClientAppService(mock(ApplicationRepository.class), clients,
                mock(OAuthClientSecretService.class), mock(AdministratorRepository.class),
                mock(AdministratorAuthorizationService.class), sessions);
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        OAuthClientAppService app = (OAuthClientAppService) factory.getProxy();
        jdbc.execute("CREATE TRIGGER " + trigger + " BEFORE UPDATE ON db_iam_authorization FOR EACH ROW "
                + "BEGIN IF NEW.oauth_client_id = '" + clientId + "' THEN "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'T7 injected revocation write failure'; END IF; END");
        try {
            assertThatThrownBy(() -> app.disable(new OAuthClientDisableCmd(clientId)))
                    .hasStackTraceContaining("T7 injected revocation write failure");
            assertThat(clients.find(new OAuthClientId(clientId)).orElseThrow().isActive()).isTrue();
            assertThat(repository.find(authorization.getId()).orElseThrow().isRevoked()).isFalse();
        } finally {
            jdbc.execute("DROP TRIGGER " + trigger);
        }
    }

    @Test
    void audienceInvalidationAndRollbackUseActualJoinSql() {
        String clientId = "aud-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO db_iam_oauth_client(oauth_client_id,app_id,audience_app_id,name,client_type,
                client_secret_hash,grant_types,scopes,redirect_uris,post_logout_redirect_uris,status)
                VALUES (?,10004,10004,'audience client','CONFIDENTIAL','digest','CLIENT_CREDENTIALS',
                'ops:broker:read','[]','[]',1)
                """, clientId);
        OAuthAuthorization authorization = authorization(clientId);
        repository.save(authorization);
        jdbc.update("UPDATE db_iam_authorization SET principal_type='ADMINISTRATOR', principal_name='7' WHERE authorization_id=?",
                authorization.getId().value());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            sessions.revokeAudience(new AppId(10004L), Instant.now());
            assertThat(jdbc.queryForObject("SELECT status FROM db_iam_authorization WHERE authorization_id=?",
                    Integer.class, authorization.getId().value())).isEqualTo(2);
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT status FROM db_iam_authorization WHERE authorization_id=?",
                Integer.class, authorization.getId().value())).isEqualTo(1);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                sessions.revokeAudience(new AppId(10004L), Instant.now()));
        assertThat(jdbc.queryForObject("SELECT status FROM db_iam_authorization WHERE authorization_id=?",
                Integer.class, authorization.getId().value())).isEqualTo(2);
    }

    @Test
    void administratorDisableRollsBackAndCommitsItsAuthorizationsOnly() {
        long administratorId = Math.abs(UUID.randomUUID().getMostSignificantBits() >>> 16);
        String username = "admin" + administratorId;
        jdbc.update("""
                INSERT INTO db_iam_administrator(administrator_id,username,email,password_hash,status)
                VALUES (?,?,?,'digest',1)
                """, administratorId, username, username + "@example.test");
        OAuthAuthorization affected = authorization("admin-client-" + UUID.randomUUID());
        OAuthAuthorization unrelated = authorization("other-admin-client-" + UUID.randomUUID());
        repository.save(affected);
        repository.save(unrelated);
        jdbc.update("UPDATE db_iam_authorization SET principal_type='ADMINISTRATOR',principal_name=? WHERE authorization_id=?",
                Long.toString(administratorId), affected.getId().value());
        jdbc.update("UPDATE db_iam_authorization SET principal_type='ADMINISTRATOR',principal_name=? WHERE authorization_id=?",
                Long.toString(administratorId + 1), unrelated.getId().value());
        MysqlAdministratorRepository administrators = new MysqlAdministratorRepository(administratorRows);
        IamAdministratorRoleRepository assignments = mock(IamAdministratorRoleRepository.class);
        AdministratorAppService target = new AdministratorAppService(administrators, sessions,
                mock(PasswordService.class), new AdministratorService(mock(IamRoleRepository.class), assignments),
                mock(AuthenticationRestriction.class), assignments);
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        AdministratorAppService app = (AdministratorAppService) factory.getProxy();
        AdministratorDisableCmd command = new AdministratorDisableCmd(administratorId);
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            app.disable(command);
            throw new IllegalStateException("rollback administrator disable");
        })).isInstanceOf(IllegalStateException.class).hasMessage("rollback administrator disable");
        assertThat(administrators.find(new AdministratorId(administratorId)).orElseThrow().isActive()).isTrue();
        assertThat(authorizationStatus(affected)).isEqualTo(1);
        app.disable(command);
        assertThat(administrators.find(new AdministratorId(administratorId)).orElseThrow().isActive()).isFalse();
        assertThat(authorizationStatus(affected)).isEqualTo(2);
        assertThat(authorizationStatus(unrelated)).isEqualTo(1);
    }

    @Autowired private DbIamAdministratorService administratorRows;
    @Autowired private MysqlApplicationRepository applications;
    @Autowired private DbIamApplicationPermissionService permissions;
    @Autowired private DbIamApplicationRolePermissionService rolePermissions;

    @Test
    void permissionCatalogWithdrawalRollsBackAndCommitsWithAudienceInvalidation() {
        long appId = Math.abs(UUID.randomUUID().getMostSignificantBits() >>> 16);
        String clientId = "catalog-" + UUID.randomUUID();
        jdbc.update("INSERT INTO db_iam_app(app_id,app_key,name,status) VALUES (?,?,'Catalog test',1)",
                appId, "catalog" + UUID.randomUUID().toString().replace("-", ""));
        jdbc.update("""
                INSERT INTO db_iam_oauth_client(oauth_client_id,app_id,audience_app_id,name,client_type,
                client_secret_hash,grant_types,scopes,redirect_uris,post_logout_redirect_uris,status)
                VALUES (?,?,?,'Catalog test','CONFIDENTIAL','digest','CLIENT_CREDENTIALS',
                'ops:broker:read','[]','[]',1)
                """, clientId, appId, appId);
        jdbc.update("""
                INSERT INTO db_iam_application_permission(permission_id,app_id,code,name,description,status)
                VALUES (?,?,'ops:broker:read','Read','Read broker',1)
                """, appId, appId);
        OAuthAuthorization affected = authorization(clientId);
        OAuthAuthorization unrelated = authorization("other-" + clientId);
        repository.save(affected);
        repository.save(unrelated);
        jdbc.update("UPDATE db_iam_authorization SET principal_type='ADMINISTRATOR',principal_name='7' WHERE authorization_id IN (?,?)",
                affected.getId().value(), unrelated.getId().value());
        MysqlApplicationPermissionRepository permissionRepository = new MysqlApplicationPermissionRepository(
                permissions, ApplicationPermissionDomainTransformer.INSTANCE, rolePermissions);
        ApplicationPermissionAppService target = new ApplicationPermissionAppService(applications, clients,
                permissionRepository, new ApplicationPermissionService(permissionRepository, mock(SnowflakeId.class)), sessions);
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        ApplicationPermissionAppService app = (ApplicationPermissionAppService) factory.getProxy();
        ApplicationPermissionCatalogSyncCmd command = new ApplicationPermissionCatalogSyncCmd(clientId, List.of());

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            app.synchronize(command);
            throw new IllegalStateException("force rollback after both writes");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("force rollback after both writes");
        assertThat(jdbc.queryForObject("SELECT status FROM db_iam_application_permission WHERE permission_id=?",
                Integer.class, appId)).isEqualTo(1);
        assertThat(authorizationStatus(affected)).isEqualTo(1);

        app.synchronize(command);
        assertThat(jdbc.queryForObject("SELECT status FROM db_iam_application_permission WHERE permission_id=?",
                Integer.class, appId)).isEqualTo(2);
        assertThat(authorizationStatus(affected)).isEqualTo(2);
        assertThat(authorizationStatus(unrelated)).isEqualTo(1);
    }

    @Autowired private DbIamApplicationRoleService roleRows;
    @Autowired private DbIamApplicationAdministratorRoleService assignmentRows;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void roleWithdrawalAndSessionInvalidationShareTransaction(boolean removeAssignment) {
        long roleId = UUID.randomUUID().getMostSignificantBits() >>> 16;
        String key = "role" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO db_iam_app(app_id,app_key,name,status) VALUES (?,?,'Role test',1)", roleId, key);
        jdbc.update("""
                INSERT INTO db_iam_application_role(role_id,app_id,code,name,type,status)
                VALUES (?,?,'operator','Operator',2,1)
                """, roleId, roleId);
        jdbc.update("INSERT INTO db_iam_application_role_permission(role_id,permission_id) VALUES (?,?)", roleId, roleId);
        jdbc.update("INSERT INTO db_iam_application_administrator_role(administrator_id,role_id) VALUES (?,?)", roleId, roleId);
        OAuthAuthorization affected = authorization(key);
        OAuthAuthorization unrelated = authorization("other-" + key);
        repository.save(affected);
        repository.save(unrelated);
        jdbc.update("UPDATE db_iam_authorization SET principal_type='ADMINISTRATOR',principal_name=? WHERE authorization_id=?",
                Long.toString(roleId), affected.getId().value());
        jdbc.update("UPDATE db_iam_authorization SET principal_type='ADMINISTRATOR',principal_name=? WHERE authorization_id=?",
                Long.toString(roleId + 1), unrelated.getId().value());
        MysqlApplicationPermissionRepository permissionRepository = new MysqlApplicationPermissionRepository(
                permissions, ApplicationPermissionDomainTransformer.INSTANCE, rolePermissions);
        jdbc.update("""
                INSERT INTO db_iam_administrator(administrator_id,username,email,password_hash,status)
                VALUES (?,?,?,'digest',1)
                """, roleId, key, key + "@example.test");
        MysqlApplicationRoleRepository roles = new MysqlApplicationRoleRepository(
                roleRows, rolePermissions, ApplicationRoleDomainTransformer.INSTANCE);
        MysqlApplicationAdministratorRoleRepository assignments = new MysqlApplicationAdministratorRoleRepository(assignmentRows);
        ApplicationRoleAppService target = new ApplicationRoleAppService(applications,
                new ApplicationPermissionService(permissionRepository, mock(SnowflakeId.class)), roles,
                mock(SnowflakeId.class), assignments, sessions,
                new ApplicationAdministratorRoleService(applications,
                        new MysqlAdministratorRepository(administratorRows), roles, assignments));
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        ApplicationRoleAppService app = (ApplicationRoleAppService) factory.getProxy();
        Runnable withdraw = removeAssignment
                ? () -> app.changeAdministratorRoles(new ApplicationRoleAssignmentChangeCmd(roleId, roleId, Set.of()))
                : () -> app.update(new ApplicationRoleUpdateCmd(roleId, "Reader", Set.of()));
        String relationTable = removeAssignment ? "db_iam_application_administrator_role" : "db_iam_application_role_permission";
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            withdraw.run();
            throw new IllegalStateException("rollback role withdrawal");
        })).isInstanceOf(IllegalStateException.class).hasMessage("rollback role withdrawal");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + relationTable + " WHERE role_id=? AND is_deleted=0",
                Integer.class, roleId)).isEqualTo(1);
        assertThat(authorizationStatus(affected)).isEqualTo(1);
        withdraw.run();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + relationTable + " WHERE role_id=? AND is_deleted=0",
                Integer.class, roleId)).isZero();
        assertThat(authorizationStatus(affected)).isEqualTo(2);
        assertThat(authorizationStatus(unrelated)).isEqualTo(1);
    }

    private int authorizationStatus(OAuthAuthorization authorization) {
        return jdbc.queryForObject("SELECT status FROM db_iam_authorization WHERE authorization_id=?",
                Integer.class, authorization.getId().value());
    }

    private OAuthAuthorization authorization(String clientId) {
        return OAuthAuthorization.builder().id(new OAuthAuthorizationId(UUID.randomUUID().toString()))
                .appId(new AppId(10004L)).clientId(new OAuthClientId(clientId))
                .principal(new OAuthPrincipal(OAuthPrincipalType.CLIENT, "im-ops-cli"))
                .grantType(OAuthGrantType.CLIENT_CREDENTIALS).scopes(Set.of(new OAuthScope("ops:broker:read")))
                .status(OAuthAuthorizationStatus.ACTIVE).build();
    }
}
