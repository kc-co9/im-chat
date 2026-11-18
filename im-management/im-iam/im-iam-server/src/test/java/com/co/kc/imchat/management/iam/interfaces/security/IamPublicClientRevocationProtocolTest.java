package com.co.kc.imchat.management.iam.interfaces.security;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.co.kc.imchat.management.iam.application.OAuthAuthorizationAppService;
import com.co.kc.imchat.management.iam.domain.session.service.OAuthAuthorizationService;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlAdministratorRepository;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlOAuthAuthorizationRepository;
import com.co.kc.imchat.management.iam.infrastructure.domain.repository.MysqlOAuthClientRepository;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamOAuthAuthorizationMapper;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamAdministratorService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamOAuthAuthorizationService;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.service.DbIamOAuthClientService;
import com.co.kc.imchat.management.iam.infrastructure.security.oauth.adapter.OAuthAuthorizationServiceAdapter;
import com.co.kc.imchat.management.iam.infrastructure.security.token.Sha256OAuthTokenDigester;
import com.co.kc.imchat.management.iam.transformer.infrastructure.OAuthAuthorizationTransformer;
import com.co.kc.imchat.plugin.datasource.ImDatasourceAutoConfiguration;
import com.co.kc.imchat.plugin.identity.ImIdentityAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import com.co.kc.imchat.management.iam.model.cqrs.query.OAuthTokenQuery;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialType;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClientType;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthClientRegistrationDTO;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthAuthorizationDTO;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthAuthorizationSaveCmd;
import com.co.kc.imchat.management.iam.transformer.infrastructure.OAuthRegisteredClientTransformer;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 生产协议链与授权应用服务经过实际 MySQL；仅用户登录和 Claims 查询使用受控 fixture。 */
@EnabledIfSystemProperty(named = "im.test.local.mysql", matches = "true")
@SpringBootTest(classes = IamPublicClientRevocationProtocolTest.TestApplication.class, properties = {
        "spring.datasource.url=${im.test.mysql.url}", "spring.datasource.username=root", "spring.datasource.password=",
        "spring.sql.init.mode=never", "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false", "spring.cloud.discovery.enabled=false",
        "im.datasource.sharding.enabled=false"})
@AutoConfigureMockMvc
public class IamPublicClientRevocationProtocolTest {
    private static final String REDIRECT = "http://127.0.0.1:8765/oauth/callback";
    private static final String VERIFIER = "0123456789012345678901234567890123456789012345678901234567890123";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RaceGate gate;
    @Autowired private RegisteredClientRepository registeredClients;
    @Autowired private PasswordEncoder passwords;

    @BeforeEach
    void participants() {
        jdbc.update("""
                INSERT INTO db_iam_oauth_client(oauth_client_id,app_id,audience_app_id,name,client_type,
                client_secret_hash,grant_types,scopes,redirect_uris,post_logout_redirect_uris,status)
                VALUES ('browser-client',10004,10004,'Ops CLI','PUBLIC',NULL,'AUTHORIZATION_CODE',
                'ops:broker:read','["http://127.0.0.1:8765/oauth/callback"]','[]',1)
                ON DUPLICATE KEY UPDATE status=1
                """);
        jdbc.update("""
                INSERT INTO db_iam_administrator(administrator_id,username,email,password_hash,status)
                VALUES (7,'protocolAdmin','protocol@example.test','digest',1)
                ON DUPLICATE KEY UPDATE status=1
                """);
    }

    @Test
    void committedHttpRevocationMakesFreshHttpIntrospectionInactiveAndKeepsOtherLogin() throws Exception {
        String first = issueToken();
        String other = issueToken();
        introspect(first, true);
        mvc.perform(post("/oauth2/revoke").param("client_id", "browser-client").param("token", first))
                .andExpect(status().isOk());
        introspect(first, false);
        introspect(other, true);
        assertThat(jdbc.queryForObject("SELECT status FROM db_iam_authorization WHERE access_token_digest=?",
                Integer.class, new Sha256OAuthTokenDigester().digest(first))).isEqualTo(2);
    }

    @Test
    void databaseRevocationFailureCannotReturnSuccessOrPublishInactive() throws Exception {
        String token = issueToken();
        String trigger = "t7_http_failure_" + UUID.randomUUID().toString().replace("-", "");
        String digest = new Sha256OAuthTokenDigester().digest(token);
        jdbc.execute("CREATE TRIGGER " + trigger + " BEFORE UPDATE ON db_iam_authorization FOR EACH ROW "
                + "BEGIN IF OLD.access_token_digest = '" + digest + "' AND NEW.status = 2 THEN "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'T7 HTTP revoke write failure'; END IF; END");
        try {
            assertThatThrownBy(() -> mvc.perform(post("/oauth2/revoke")
                    .param("client_id", "browser-client").param("token", token)))
                    .hasStackTraceContaining("T7 HTTP revoke write failure");
            introspect(token, true);
        } finally {
            jdbc.execute("DROP TRIGGER " + trigger);
        }
    }

    private void introspect(String token, boolean active) throws Exception {
        mvc.perform(post("/oauth2/introspect").with(httpBasic("introspection-client", "browser-secret"))
                        .param("token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(active));
        if (active) {
            mvc.perform(post("/oauth2/introspect").with(httpBasic("introspection-client", "browser-secret"))
                            .param("token", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.exp").isNumber())
                    .andExpect(jsonPath("$.aud[0]").value("imOps"))
                    .andExpect(jsonPath("$.client_id").value("browser-client"))
                    .andExpect(jsonPath("$.principal_type").value("ADMINISTRATOR"))
                    .andExpect(jsonPath("$.sub").value("7"))
                    .andExpect(jsonPath("$.scope").value("ops:broker:read"))
                    .andExpect(jsonPath("$.authorities[0]").value("ops:broker:read"));
        }
    }

    private String issueToken() throws Exception {
        MvcResult issued = exchange(issueCode("browser-client"), "browser-client");
        assertThat(issued.getResponse().getStatus()).isEqualTo(200);
        return new ObjectMapper().readTree(issued.getResponse().getContentAsByteArray()).get("access_token").asText();
    }

    private String issueCode(String clientId) throws Exception {
        MvcResult login = mvc.perform(post("/login").with(csrf()).param("username", "administrator")
                        .param("password", "password")).andExpect(status().is3xxRedirection()).andReturn();
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
        String request = UriComponentsBuilder.fromPath("/oauth2/authorize").queryParam("response_type", "code")
                .queryParam("client_id", clientId).queryParam("redirect_uri", REDIRECT)
                .queryParam("scope", "ops:broker:read").queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256").build().encode().toUriString();
        MvcResult authorized = mvc.perform(get(request).session((MockHttpSession) login.getRequest().getSession()))
                .andExpect(status().is3xxRedirection()).andReturn();
        String code = UriComponentsBuilder.fromUri(URI.create(authorized.getResponse().getRedirectedUrl()))
                .build().getQueryParams().getFirst("code");
        assertThat(code).isNotBlank();
        return code;
    }

    private MvcResult exchange(String code, String clientId) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request = post("/oauth2/token")
                .param("client_id", clientId).param("grant_type", "authorization_code").param("code", code)
                .param("redirect_uri", REDIRECT).param("code_verifier", VERIFIER);
        if (!clientId.equals("browser-client")) {
            request.with(httpBasic(clientId, "confidential-secret"));
        }
        return mvc.perform(request).andReturn();
    }

    @Test
    void concurrentSameCodeExchangeReturnsOneTokenAndRejectsStaleSnapshot() throws Exception {
        String code = issueCode("browser-client");
        String id = jdbc.queryForObject("SELECT authorization_id FROM db_iam_authorization WHERE authorization_code_digest=?",
                String.class, new Sha256OAuthTokenDigester().digest(code));
        List<MvcResult> responses = race(id, () -> exchange(code, "browser-client"));
        assertSingleWinner(responses, id, false);
    }

    @Test
    void concurrentConfidentialRefreshReturnsOneRotationAndRejectsStaleSnapshot() throws Exception {
        String clientId = "t7-confidential";
        RegisteredClient client = OAuthRegisteredClientTransformer.INSTANCE.registeredClientFrom(
                new OAuthClientRegistrationDTO(clientId, OAuthClientType.CONFIDENTIAL,
                        passwords.encode("confidential-secret"), "T7 confidential",
                        Set.of(OAuthGrantType.AUTHORIZATION_CODE, OAuthGrantType.REFRESH_TOKEN),
                        Set.of("ops:broker:read"), Set.of(REDIRECT), Set.of()));
        ((InMemoryRegisteredClientRepository) registeredClients).save(client);
        jdbc.update("""
                INSERT INTO db_iam_oauth_client(oauth_client_id,app_id,audience_app_id,name,client_type,
                client_secret_hash,grant_types,scopes,redirect_uris,post_logout_redirect_uris,status)
                VALUES (?,10004,10004,'T7 confidential','CONFIDENTIAL',?,'AUTHORIZATION_CODE REFRESH_TOKEN',
                'ops:broker:read','["http://127.0.0.1:8765/oauth/callback"]','[]',1)
                ON DUPLICATE KEY UPDATE grant_types=VALUES(grant_types),status=1
                """, clientId, passwords.encode("confidential-secret"));
        MvcResult initial = exchange(issueCode(clientId), clientId);
        assertThat(initial.getResponse().getStatus()).isEqualTo(200);
        String refresh = new ObjectMapper().readTree(initial.getResponse().getContentAsByteArray()).get("refresh_token").asText();
        String id = jdbc.queryForObject("SELECT authorization_id FROM db_iam_authorization WHERE refresh_token_digest=?",
                String.class, new Sha256OAuthTokenDigester().digest(refresh));
        assertThat(client.getTokenSettings().isReuseRefreshTokens()).isFalse();
        List<MvcResult> responses = race(id, () -> refresh(clientId, refresh));
        assertSingleWinner(responses, id, true);
        String next = new ObjectMapper().readTree(responses.stream().filter(r -> r.getResponse().getStatus() == 200)
                .findFirst().orElseThrow().getResponse().getContentAsByteArray()).get("refresh_token").asText();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM db_iam_authorization WHERE refresh_token_digest=?",
                Integer.class, new Sha256OAuthTokenDigester().digest(refresh))).isZero();
        assertThat(refresh(clientId, next).getResponse().getStatus()).isEqualTo(200);
    }

    private MvcResult refresh(String clientId, String refresh) throws Exception {
        return mvc.perform(post("/oauth2/token").with(httpBasic(clientId, "confidential-secret"))
                .param("grant_type", "refresh_token").param("refresh_token", refresh)).andReturn();
    }

    @Test
    void concurrentDuplicateRevocationRemainsSuccessfulWithoutSecondMutation() throws Exception {
        String token = issueToken();
        String id = jdbc.queryForObject("SELECT authorization_id FROM db_iam_authorization WHERE access_token_digest=?",
                String.class, new Sha256OAuthTokenDigester().digest(token));
        List<MvcResult> responses = race(id, () -> mvc.perform(post("/oauth2/revoke")
                .param("client_id", "browser-client").param("token", token)).andReturn());
        assertThat(responses).allSatisfy(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));
        Map<String, Object> terminal = jdbc.queryForMap("SELECT version,revoked_at,access_token_digest FROM db_iam_authorization WHERE authorization_id=?", id);
        assertThat(((Number) terminal.get("version")).longValue()).isEqualTo(gate.originalVersion + 1);
        introspect(token, false);
        mvc.perform(post("/oauth2/revoke").param("client_id", "browser-client").param("token", token)).andExpect(status().isOk());
        assertThat(jdbc.queryForMap("SELECT version,revoked_at,access_token_digest FROM db_iam_authorization WHERE authorization_id=?", id)).isEqualTo(terminal);
    }

    private List<MvcResult> race(String id, Callable<MvcResult> request) throws Exception {
        gate.arm(id, true, false);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<MvcResult> first = executor.submit(request);
            Future<MvcResult> second = executor.submit(request);
            try {
                gate.awaitReads();
                gate.allowReads.countDown();
                gate.awaitBlocked(jdbc);
            } finally {
                gate.release.countDown();
                gate.allowReads.countDown();
            }
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            gate.disarm();
        }
    }

    private void assertSingleWinner(List<MvcResult> responses, String id, boolean refresh) throws Exception {
        assertThat(responses.stream().map(r -> r.getResponse().getStatus()).sorted().toList()).containsExactly(200, 400);
        MvcResult winner = responses.stream().filter(r -> r.getResponse().getStatus() == 200).findFirst().orElseThrow();
        MvcResult loser = responses.stream().filter(r -> r.getResponse().getStatus() == 400).findFirst().orElseThrow();
        com.fasterxml.jackson.databind.JsonNode rejected = new ObjectMapper().readTree(loser.getResponse().getContentAsByteArray());
        assertThat(rejected.get("error").asText()).isEqualTo("invalid_grant");
        assertThat(rejected.has("access_token")).isFalse();
        assertThat(rejected.has("refresh_token")).isFalse();
        com.fasterxml.jackson.databind.JsonNode accepted = new ObjectMapper().readTree(winner.getResponse().getContentAsByteArray());
        Map<String, Object> row = jdbc.queryForMap("SELECT status,version,authorization_code_used_at,access_token_digest,refresh_token_digest FROM db_iam_authorization WHERE authorization_id=?", id);
        assertThat(((Number) row.get("status")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(gate.originalVersion + 1);
        assertThat(row.get("authorization_code_used_at")).isNotNull();
        assertThat(row.get("access_token_digest")).isEqualTo(new Sha256OAuthTokenDigester().digest(accepted.get("access_token").asText()));
        if (refresh) {
            assertThat(row.get("refresh_token_digest")).isEqualTo(new Sha256OAuthTokenDigester().digest(accepted.get("refresh_token").asText()));
        } else {
            assertThat(accepted.has("refresh_token")).isFalse();
            introspect(accepted.get("access_token").asText(), true);
        }
    }

    /** 测试侧事务内闸门：原始双读取与目标行锁等待分别取证，不修改生产事务语义。 */
    public static final class RaceGate {
        public volatile String target;
        private boolean readGate;
        private boolean rollback;
        private final AtomicInteger writes = new AtomicInteger();
        private final List<Long> readVersions = new ArrayList<>();
        private final Set<Thread> readers = new HashSet<>();
        private final List<String> readEvidence = new ArrayList<>();
        public CountDownLatch reads;
        public CountDownLatch allowReads;
        public CountDownLatch held;
        public CountDownLatch release;
        private volatile long blocker;
        private volatile long requester;
        public long originalVersion;

        public void arm(String id, boolean gateReads, boolean rollbackFirst) {
            target = id;
            readGate = gateReads;
            rollback = rollbackFirst;
            writes.set(0);
            readVersions.clear();
            readers.clear();
            readEvidence.clear();
            reads = new CountDownLatch(2);
            allowReads = new CountDownLatch(1);
            held = new CountDownLatch(1);
            release = new CountDownLatch(1);
            blocker = 0;
            requester = 0;
        }

        public void disarm() { target = null; }

        public Object invoke(org.aopalliance.intercept.MethodInvocation invocation, JdbcTemplate jdbc) throws Throwable {
            String method = invocation.getMethod().getName();
            if (method.equals("queryByToken")) {
                Object result = invocation.proceed();
                if (readGate && target != null && result instanceof java.util.Optional<?> optional
                        && optional.orElse(null) instanceof OAuthAuthorizationDTO dto && target.equals(dto.authorizationId())) {
                    long version = jdbc.queryForObject("SELECT version FROM db_iam_authorization WHERE authorization_id=?", Long.class, target);
                    OAuthTokenQuery query =
                            (OAuthTokenQuery) invocation.getArguments()[0];
                    assertThat(dto.revoked()).isFalse();
                    if (query.credentialType() == OAuthCredentialType.AUTHORIZATION_CODE) {
                        assertThat(dto.authorizationCode()).isNotNull();
                        assertThat(dto.authorizationCode().digest()).isEqualTo(query.tokenDigest());
                        assertThat(dto.authorizationCodeConsumed()).isFalse();
                    } else if (query.credentialType() == OAuthCredentialType.REFRESH_TOKEN) {
                        assertThat(dto.refreshToken()).isNotNull();
                        assertThat(dto.refreshToken().digest()).isEqualTo(query.tokenDigest());
                    } else {
                        assertThat(dto.accessToken()).isNotNull();
                        assertThat(dto.accessToken().digest()).isEqualTo(query.tokenDigest());
                    }
                    boolean firstRead;
                    synchronized (readVersions) {
                        firstRead = readers.add(Thread.currentThread());
                        if (firstRead) {
                            readVersions.add(version);
                            originalVersion = version;
                            readEvidence.add("thread=" + Thread.currentThread().threadId() + " type=" + query.credentialType()
                                    + " digest=" + query.tokenDigest() + " codeConsumed=" + dto.authorizationCodeConsumed());
                        }
                    }
                    if (firstRead) {
                        reads.countDown();
                        await(allowReads);
                    }
                }
                return result;
            }
            String id = method.equals("save") ? ((OAuthAuthorizationSaveCmd) invocation.getArguments()[0]).authorizationId()
                    : method.equals("revoke") ? ((com.co.kc.imchat.management.iam.model.cqrs.command.OAuthAuthorizationRevokeCmd) invocation.getArguments()[0]).authorizationId() : null;
            if (target == null || !target.equals(id)) { return invocation.proceed(); }
            int position = writes.incrementAndGet();
            long connection = jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class);
            if (position == 1) {
                blocker = connection;
            } else {
                requester = connection;
                // 入口顺序不代表取得数据库行锁的顺序；第二写入须等首写真实 UPDATE 完成。
                await(held);
            }
            Object result = invocation.proceed();
            if (position == 1) {
                held.countDown();
                await(release);
                if (rollback) { TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); }
            }
            return result;
        }

        public void awaitReads() throws InterruptedException {
            await(reads);
            assertThat(readVersions).hasSize(2);
            assertThat(readVersions.get(0)).isEqualTo(readVersions.get(1));
        }

        public void awaitBlocked(JdbcTemplate jdbc) throws InterruptedException {
            await(held);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            List<Map<String, Object>> proof = List.of();
            while (System.nanoTime() < deadline) {
                if (requester != 0) {
                    proof = jdbc.queryForList("""
                            SELECT rt.PROCESSLIST_ID requester,bt.PROCESSLIST_ID blocker,rl.OBJECT_SCHEMA,
                                   rl.OBJECT_NAME,rl.INDEX_NAME,rl.LOCK_DATA,bl.LOCK_DATA blocking_data
                            FROM performance_schema.data_lock_waits w
                            JOIN performance_schema.data_locks rl ON rl.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID AND rl.ENGINE=w.ENGINE
                            JOIN performance_schema.data_locks bl ON bl.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID AND bl.ENGINE=w.ENGINE
                            JOIN performance_schema.threads rt ON rt.THREAD_ID=w.REQUESTING_THREAD_ID
                            JOIN performance_schema.threads bt ON bt.THREAD_ID=w.BLOCKING_THREAD_ID
                            WHERE rt.PROCESSLIST_ID=? AND bt.PROCESSLIST_ID=?
                              AND rl.OBJECT_SCHEMA=DATABASE() AND rl.OBJECT_NAME='db_iam_authorization'
                              AND rl.INDEX_NAME='uk_iam_authorization_identifier'
                              AND rl.LOCK_DATA LIKE CONCAT('%', ?, '%') AND rl.LOCK_DATA=bl.LOCK_DATA
                            """, requester, blocker, target);
                    if (!proof.isEmpty()) { break; }
                }
                Thread.onSpinWait();
            }
            if (proof.isEmpty()) {
                assertThat(proof).withFailMessage(
                        "target authorization %s requester %s blocked by %s reads=%s waits=%s",
                        target, requester, blocker, readVersions, jdbc.queryForList("""
                        SELECT rt.PROCESSLIST_ID requester,bt.PROCESSLIST_ID blocker,rl.INDEX_NAME,
                               rl.LOCK_DATA,bl.LOCK_DATA blocking_data
                        FROM performance_schema.data_lock_waits w
                        JOIN performance_schema.data_locks rl ON rl.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID AND rl.ENGINE=w.ENGINE
                        JOIN performance_schema.data_locks bl ON bl.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID AND bl.ENGINE=w.ENGINE
                        JOIN performance_schema.threads rt ON rt.THREAD_ID=w.REQUESTING_THREAD_ID
                        JOIN performance_schema.threads bt ON bt.THREAD_ID=w.BLOCKING_THREAD_ID
                        WHERE rl.OBJECT_SCHEMA=DATABASE() AND rl.OBJECT_NAME='db_iam_authorization'
                          AND (rt.PROCESSLIST_ID IN (?,?) OR bt.PROCESSLIST_ID IN (?,?))
                        """, requester, blocker, requester, blocker)).isNotEmpty();
            }
            assertThat(proof).as("target authorization %s requester %s blocked by %s reads=%s credentials=%s",
                    target, requester, blocker, readVersions, readEvidence).isNotEmpty();
        }

        public static void await(CountDownLatch latch) throws InterruptedException {
            assertThat(latch.await(15, TimeUnit.SECONDS)).as("bounded T7 race gate").isTrue();
        }
    }

    public static OAuthAuthorizationAppService transactionalApp(OAuthAuthorizationAppService target,
            PlatformTransactionManager transactions, JdbcTemplate jdbc, RaceGate gate) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        factory.addAdvice((MethodInterceptor) invocation -> gate.invoke(invocation, jdbc));
        return (OAuthAuthorizationAppService) factory.getProxy();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class,
            RedissonAutoConfigurationV2.class, ImIdentityAutoConfiguration.class})
    @Import({IamPublicClientProtocolTest.SecurityConfiguration.class, DbIamOAuthAuthorizationService.class,
            DbIamOAuthClientService.class, DbIamAdministratorService.class, MysqlOAuthAuthorizationRepository.class,
            MysqlOAuthClientRepository.class, MysqlAdministratorRepository.class})
    @ImportAutoConfiguration({MybatisPlusAutoConfiguration.class, ImDatasourceAutoConfiguration.class})
    @MapperScan(basePackageClasses = DbIamOAuthAuthorizationMapper.class)
    static class TestApplication {
        @Bean
        RaceGate raceGate() { return new RaceGate(); }

        @Bean
        @Primary
        OAuth2AuthorizationService persistentAuthorizationService(MysqlOAuthAuthorizationRepository authorizations,
                MysqlOAuthClientRepository clients, MysqlAdministratorRepository administrators,
                PlatformTransactionManager transactions, RegisteredClientRepository registeredClients, JdbcTemplate jdbc, RaceGate gate) {
            OAuthAuthorizationAppService target = new OAuthAuthorizationAppService(authorizations, clients,
                    new OAuthAuthorizationService(administrators));
            OAuthAuthorizationAppService app = transactionalApp(target, transactions, jdbc, gate);
            Sha256OAuthTokenDigester digester = new Sha256OAuthTokenDigester();
            return new OAuthAuthorizationServiceAdapter(app, registeredClients,
                    new OAuthAuthorizationTransformer(digester, Clock.systemUTC()), digester);
        }
    }
}
