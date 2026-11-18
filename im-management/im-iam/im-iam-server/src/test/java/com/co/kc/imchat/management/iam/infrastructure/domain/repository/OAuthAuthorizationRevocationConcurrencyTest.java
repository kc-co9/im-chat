package com.co.kc.imchat.management.iam.infrastructure.domain.repository;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientId;
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
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.co.kc.imchat.management.iam.application.OAuthAuthorizationAppService;
import com.co.kc.imchat.management.iam.domain.application.repository.OAuthClientRepository;
import com.co.kc.imchat.management.iam.domain.session.service.OAuthAuthorizationService;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAccessToken;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialDigest;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialPeriod;
import com.co.kc.imchat.management.iam.infrastructure.security.oauth.adapter.OAuthAuthorizationServiceAdapter;
import com.co.kc.imchat.management.iam.infrastructure.security.oauth.introspector.IamOpaqueTokenIntrospector;
import com.co.kc.imchat.management.iam.infrastructure.security.token.Sha256OAuthTokenDigester;
import com.co.kc.imchat.management.iam.transformer.infrastructure.OAuthAuthorizationTransformer;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import java.time.Clock;
import com.co.kc.imchat.management.iam.interfaces.security.IamPublicClientRevocationProtocolTest;
import com.co.kc.imchat.management.iam.interfaces.security.IamPublicClientRevocationProtocolTest.RaceGate;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthAuthorizationRevokeCmd;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import java.util.concurrent.ExecutionException;
import java.util.Map;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 必须连接任务专属真实 MySQL；H2 与 mock 不提供本测试的并发证据。 */
@EnabledIfSystemProperty(named = "im.test.local.mysql", matches = "true")
@SpringJUnitConfig
@Import({DbIamOAuthAuthorizationService.class, MysqlOAuthAuthorizationRepository.class})
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
        JacksonAutoConfiguration.class, ImDatasourceAutoConfiguration.class})
@MapperScan(basePackageClasses = DbIamOAuthAuthorizationMapper.class)
@TestPropertySource(properties = {
        "spring.datasource.url=${im.test.mysql.url}", "spring.datasource.username=root",
        "spring.datasource.password=", "spring.sql.init.mode=never"})
class OAuthAuthorizationRevocationConcurrencyTest {
    @Autowired private MysqlOAuthAuthorizationRepository repository;
    @Autowired private DbIamOAuthAuthorizationService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void activeUpdateAdvancesVersionAndRemainsSaveable() {
        OAuthAuthorization authorization = authorization();
        repository.save(authorization);
        long original = authorization.getRowVersion();
        repository.save(authorization);
        assertThat(authorization.getRowVersion()).isGreaterThan(original);
        repository.save(authorization);
        assertThat(repository.find(authorization.getId()).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void staleActiveSaveCannotReviveCommittedRevocation() {
        OAuthAuthorization authorization = authorization();
        repository.save(authorization);
        OAuthAuthorization stale = repository.find(authorization.getId()).orElseThrow();
        service.revoke(authorization.getId().value(), Instant.now());
        stale.setRowVersion(repository.find(authorization.getId()).orElseThrow().getRowVersion());
        assertThatThrownBy(() -> repository.save(stale)).isInstanceOf(RuntimeException.class);
        assertThat(repository.find(authorization.getId()).orElseThrow().isRevoked()).isTrue();
    }

    @Test
    void revocationAndUnrelatedLoginRemainIsolatedAfterCommit() {
        OAuthAuthorization first = authorization();
        OAuthAuthorization second = authorization();
        repository.save(first);
        repository.save(second);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                service.revoke(first.getId().value(), Instant.now()));
        assertThat(repository.find(first.getId()).orElseThrow().isRevoked()).isTrue();
        assertThat(repository.find(second.getId()).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void rollbackDoesNotPublishRevocation() {
        OAuthAuthorization authorization = authorization();
        repository.save(authorization);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.revoke(authorization.getId().value(), Instant.now());
            status.setRollbackOnly();
        });
        assertThat(repository.find(authorization.getId()).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void revocationWaitsForInsertTransactionThenRemainsIrreversible() throws Exception {
        OAuthAuthorization authorization = authorization();
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> inserting = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                repository.save(authorization);
                inserted.countDown();
                try {
                    assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> revoking = executor.submit(() -> service.revoke(authorization.getId().value(), Instant.now()));
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting = false;
                while (System.nanoTime() < deadline && !revoking.isDone()) {
                    waiting = jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits",
                            Integer.class) > 0;
                    if (waiting) {
                        break;
                    }
                }
                assertThat(waiting).as("MySQL must observe the revoke waiting on the uncommitted insert").isTrue();
                assertThat(revoking.isDone()).isFalse();
            } finally {
                release.countDown();
            }
            inserting.get(10, TimeUnit.SECONDS);
            revoking.get(10, TimeUnit.SECONDS);
        }
        assertThat(repository.find(authorization.getId()).orElseThrow().isRevoked()).isTrue();
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.0.");
    }

    @Test
    void committedRevocationMakesFreshIntrospectionInactiveWithoutRevokingOtherLogin() {
        String raw = UUID.randomUUID().toString();
        Sha256OAuthTokenDigester digester = new Sha256OAuthTokenDigester();
        OAuthAuthorization authorization = authorization();
        Instant now = Instant.now();
        authorization.changeAccessToken(new OAuthAccessToken(new OAuthCredentialDigest(digester.digest(raw)),
                new OAuthCredentialPeriod(now, now.plusSeconds(300)),
                Map.of("sub", "im-ops-cli", "principal_type", "MACHINE", "aud", java.util.List.of("imOps"),
                        "client_id", "im-ops-cli", "authorities", java.util.List.of())));
        repository.save(authorization);
        RegisteredClientRepository clients = mock(RegisteredClientRepository.class);
        RegisteredClient client = RegisteredClient.withId("im-ops-cli").clientId("im-ops-cli")
                .clientSecret("digest").authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("ops:broker:read").build();
        when(clients.findById("im-ops-cli")).thenReturn(client);
        OAuthAuthorizationAppService app = new OAuthAuthorizationAppService(repository,
                mock(OAuthClientRepository.class), mock(OAuthAuthorizationService.class));
        IamOpaqueTokenIntrospector introspector = new IamOpaqueTokenIntrospector(new OAuthAuthorizationServiceAdapter(
                app, clients, new OAuthAuthorizationTransformer(digester, Clock.systemUTC()), digester));
        assertThat(introspector.introspect(raw).getAttributes().get("principal_type")).isEqualTo("MACHINE");
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                service.revoke(authorization.getId().value(), Instant.now()));
        assertThatThrownBy(() -> introspector.introspect(raw)).isInstanceOf(BadOpaqueTokenException.class);
    }

    @Test
    void existingRowSaveCommitsBeforeWaitingRevokeLeavesReplacementInactive() throws Exception {
        existingRowOrder(true, false);
    }

    @Test
    void existingRowRevokeCommitsBeforeWaitingSaveRejectsReplacement() throws Exception {
        existingRowOrder(false, false);
    }

    @Test
    void rolledBackExistingRowSaveLetsWaitingRevokeCommit() throws Exception {
        existingRowOrder(true, true);
    }

    @Test
    void rolledBackExistingRowRevokeLetsWaitingSaveCommit() throws Exception {
        existingRowOrder(false, true);
    }

    private void existingRowOrder(boolean saveFirst, boolean rollbackFirst) throws Exception {
        Sha256OAuthTokenDigester digester = new Sha256OAuthTokenDigester();
        String original = UUID.randomUUID().toString();
        String replacement = UUID.randomUUID().toString();
        OAuthAuthorization authorization = authorization();
        Instant now = Instant.now();
        authorization.changeAccessToken(new OAuthAccessToken(new OAuthCredentialDigest(digester.digest(original)),
                new OAuthCredentialPeriod(now, now.plusSeconds(300)),
                Map.of("sub", "im-ops-cli", "principal_type", "MACHINE", "aud", java.util.List.of("imOps"),
                        "client_id", "im-ops-cli", "authorities", java.util.List.of())));
        repository.save(authorization);
        OAuthAuthorization unrelated = authorization();
        repository.save(unrelated);
        RegisteredClient client = RegisteredClient.withId("im-ops-cli").clientId("im-ops-cli")
                .clientSecret("digest").authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("ops:broker:read").build();
        RegisteredClientRepository clients = mock(RegisteredClientRepository.class);
        when(clients.findById("im-ops-cli")).thenReturn(client);
        RaceGate gate = new RaceGate();
        OAuthAuthorizationAppService target = new OAuthAuthorizationAppService(repository,
                mock(OAuthClientRepository.class), mock(OAuthAuthorizationService.class));
        OAuthAuthorizationAppService app = IamPublicClientRevocationProtocolTest.transactionalApp(target,
                transactionManager, jdbc, gate);
        OAuthAuthorizationServiceAdapter adapter = new OAuthAuthorizationServiceAdapter(app, clients,
                new OAuthAuthorizationTransformer(digester, Clock.systemUTC()), digester);
        OAuth2Authorization snapshot = adapter.findByToken(original, OAuth2TokenType.ACCESS_TOKEN);
        OAuth2Authorization updated = OAuth2Authorization.from(snapshot)
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, replacement, now, now.plusSeconds(300)),
                        metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, snapshot.getAccessToken().getClaims()))
                .build();
        long originalVersion = authorization.getRowVersion();
        gate.arm(authorization.getId().value(), false, rollbackFirst);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Runnable saving = () -> adapter.save(updated);
            Runnable revoking = () -> app.revoke(new OAuthAuthorizationRevokeCmd(authorization.getId().value()));
            Future<?> first = executor.submit(saveFirst ? saving : revoking);
            Future<?> second;
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                while (!gate.held.await(25, TimeUnit.MILLISECONDS)) {
                    if (first.isDone()) {
                        first.get();
                        throw new AssertionError("First writer completed without holding the target transaction");
                    }
                    assertThat(System.nanoTime()).as("bounded first writer gate").isLessThan(deadline);
                }
                second = executor.submit(saveFirst ? revoking : saving);
                gate.awaitBlocked(jdbc);
                assertThat(second.isDone()).isFalse();
            } finally {
                gate.release.countDown();
            }
            first.get(15, TimeUnit.SECONDS);
            if (!saveFirst && !rollbackFirst) {
                assertThatThrownBy(() -> second.get(15, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .cause().isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                                exception -> assertThat(exception.getError().getErrorCode()).isEqualTo("invalid_grant"));
            } else {
                second.get(15, TimeUnit.SECONDS);
            }
        } finally {
            gate.release.countDown();
            gate.disarm();
        }
        OAuthAuthorization stored = repository.find(authorization.getId()).orElseThrow();
        boolean active = !saveFirst && rollbackFirst;
        assertThat(stored.isRevoked()).isEqualTo(!active);
        String expected = saveFirst && rollbackFirst || !saveFirst && !rollbackFirst ? original : replacement;
        assertThat(stored.getAccessToken().digest().value()).isEqualTo(digester.digest(expected));
        assertThat(stored.getRowVersion()).isEqualTo(originalVersion + (rollbackFirst || !saveFirst ? 1 : 2));
        assertThat(repository.find(unrelated.getId()).orElseThrow().isRevoked()).isFalse();
        IamOpaqueTokenIntrospector introspector = new IamOpaqueTokenIntrospector(adapter);
        if (active) {
            assertThat(introspector.introspect(replacement).getAttributes().get("principal_type")).isEqualTo("MACHINE");
        } else {
            assertThatThrownBy(() -> introspector.introspect(replacement)).isInstanceOf(BadOpaqueTokenException.class);
            assertThatThrownBy(() -> introspector.introspect(original)).isInstanceOf(BadOpaqueTokenException.class);
        }
    }

    private OAuthAuthorization authorization() {
        return OAuthAuthorization.builder().id(new OAuthAuthorizationId(UUID.randomUUID().toString()))
                .appId(new AppId(10004L)).clientId(new OAuthClientId("im-ops-cli"))
                .principal(new OAuthPrincipal(OAuthPrincipalType.CLIENT, "im-ops-cli"))
                .grantType(OAuthGrantType.CLIENT_CREDENTIALS).scopes(Set.of(new OAuthScope("ops:broker:read")))
                .status(OAuthAuthorizationStatus.ACTIVE).build();
    }
}
