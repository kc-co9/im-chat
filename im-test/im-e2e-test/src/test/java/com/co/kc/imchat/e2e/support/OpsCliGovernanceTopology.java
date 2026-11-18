package com.co.kc.imchat.e2e.support;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.cloud.nacos.registry.NacosRegistration;
import com.alibaba.cloud.nacos.registry.NacosRegistrationCustomizer;
import com.alibaba.cloud.nacos.registry.NacosServiceRegistry;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import com.co.kc.imchat.broker.ImBrokerApplication;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult;
import com.co.kc.imchat.broker.application.cluster.BrokerMembershipAppService;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerClusterOperation;
import com.co.kc.imchat.broker.domain.cluster.model.ClusterOperationId;
import com.co.kc.imchat.broker.interfaces.scheduler.BrokerMembershipScheduler;
import org.springframework.beans.factory.config.BeanPostProcessor;
import com.co.kc.imchat.management.audit.sdk.model.AuditEvent;
import com.co.kc.imchat.management.audit.sdk.transport.AuditTransport;
import com.co.kc.imchat.management.iam.ImIamApplication;
import com.co.kc.imchat.management.iam.application.OAuthClientAppService;
import com.co.kc.imchat.management.iam.model.cqrs.command.OAuthClientDisableCmd;
import com.co.kc.imchat.management.ops.ImOpsApplication;
import com.co.kc.imchat.management.ops.cli.auth.CliLoginSession;
import com.co.kc.imchat.management.ops.cli.auth.LoopbackAuthorizationCallback;
import com.co.kc.imchat.management.ops.cli.http.OpsCliClient;
import com.co.kc.imchat.management.ops.domain.model.BrokerManagementEndpoint;
import com.co.kc.imchat.management.ops.infrastructure.client.HttpBrokerManagementClient;
import com.co.kc.imchat.management.ops.infrastructure.config.beans.OpsHttpBeans;
import com.co.kc.imchat.management.ops.infrastructure.config.properties.OpsBrokerProperties;
import com.co.kc.imchat.service.message.facade.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * 首个治理旅程的独享设施。真实 IAM/MySQL/Redis、Ops 安全链及 Broker HTTP/Raft；
 * 仅替换浏览器动作、Nacos DiscoveryClient、Audit 接收端及 Broker 的 Message 外部服务。
 * 单节点开发 Bolt 不证明 quorum、MTLS 或 Audit Server 持久化。
 */
public final class OpsCliGovernanceTopology implements AutoCloseable {
    public static final String BROKER_ID = "governance-broker";
    public static final String LEARNER_ID = "governance-learner";
    private final boolean writeJourney;
    private boolean writeAuthority = true;
    private boolean followerJourney;
    private boolean confirmationJourney;
    private final AtomicBoolean recoveryPaused = new AtomicBoolean();
    private final AtomicInteger deferredCompletions = new AtomicInteger();
    private final AtomicInteger recoveryCalls = new AtomicInteger();
    private ClusterOperationId recoveryOperationId;
    private List<ServiceInstance> registeredInstances = List.of();
    private String discoveryMutation = "NONE";
    private final List<ConfigurableApplicationContext> brokerContexts = new ArrayList<>();
    private final ConcurrentLinkedQueue<NetworkWrite> networkWrites = new ConcurrentLinkedQueue<>();

    public record NetworkWrite(String brokerId, String path, String payload, String scope,
                               String operationId, String expectedEpoch, boolean leaderAtReceipt,
                               boolean notLeaderResponse, boolean hintMatchesOfficialLeader,
                               int responseStatus) { }

    public static OpsCliGovernanceTopology forConflictingLeaderMetadataJourney() {
        OpsCliGovernanceTopology topology = forFollowerJourney();
        topology.discoveryMutation = "CONFLICT";
        return topology;
    }

    public static OpsCliGovernanceTopology forMissingLeaderMetadataJourney() {
        OpsCliGovernanceTopology topology = forFollowerJourney();
        topology.discoveryMutation = "MISSING";
        return topology;
    }

    public static OpsCliGovernanceTopology forConfirmationJourney() {
        OpsCliGovernanceTopology topology = new OpsCliGovernanceTopology(true);
        topology.confirmationJourney = true;
        return topology;
    }

    public static OpsCliGovernanceTopology forConfirmationRecoveryJourney(String operationId) {
        OpsCliGovernanceTopology topology = forConfirmationJourney();
        topology.recoveryOperationId = new ClusterOperationId(operationId);
        topology.recoveryPaused.set(true);
        return topology;
    }

    public void allowScheduledRecovery() {
        assertThat(deferredCompletions.get()).as("terminal attempts before scheduler release").isEqualTo(1);
        assertThat(recoveryCalls.get()).as("resume calls before scheduler release").isZero();
        recoveryPaused.set(false);
    }

    public void assertScheduledRecoveryRanOnce() {
        assertThat(recoveryCalls.get()).as("resume calls for the CLI operation").isEqualTo(1);
    }

    public static OpsCliGovernanceTopology forFollowerJourney() {
        OpsCliGovernanceTopology topology = new OpsCliGovernanceTopology(true);
        topology.followerJourney = true;
        return topology;
    }

    private final java.util.concurrent.ConcurrentMap<String, ResponseObservation> responseObservations =
            new java.util.concurrent.ConcurrentHashMap<>();

    private record ResponseObservation(boolean notLeader, boolean officialMatch) { }

    public void assertBrokerWriteObserverRegistered() {
        List<ConfigurableApplicationContext> observedContexts = contexts.stream()
                .filter(context -> context.containsBean("governanceNetworkWriteObserver")).toList();
        assertThat(observedContexts).isNotEmpty();
        for (ConfigurableApplicationContext context : observedContexts) {
            FilterRegistrationBean<?> registration = context.getBean(
                    "governanceNetworkWriteObserver", FilterRegistrationBean.class);
            assertThat(registration.getUrlPatterns()).containsExactly("/management/cluster-state/brokers/*");
            org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext servletContext =
                    (org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) context;
            assertThat(servletContext.getServletContext().getFilterRegistrations().values().stream()
                    .anyMatch(filter -> filter.getClassName().equals(registration.getFilter().getClass().getName())
                            && filter.getUrlPatternMappings().contains("/management/cluster-state/brokers/*")))
                    .isTrue();
        }
    }

    public List<NetworkWrite> networkWrites() {
        return networkWrites.stream().map(write -> {
            ResponseObservation response = responseObservations.get(write.brokerId());
            return response == null ? write : new NetworkWrite(write.brokerId(), write.path(), write.payload(), write.scope(),
                    write.operationId(), write.expectedEpoch(), write.leaderAtReceipt(), response.notLeader(),
                    response.officialMatch(), write.responseStatus());
        }).toList();
    }

    @org.springframework.web.bind.annotation.ControllerAdvice
    public static final class GovernanceResponseObserver implements
            org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice<Object> {
        private final java.util.function.Consumer<Object> observer;

        public GovernanceResponseObserver(java.util.function.Consumer<Object> observer) {
            this.observer = observer;
        }

        @Override
        public boolean supports(org.springframework.core.MethodParameter returnType,
                Class<? extends org.springframework.http.converter.HttpMessageConverter<?>> converter) {
            return true;
        }

        @Override
        public Object beforeBodyWrite(Object body, org.springframework.core.MethodParameter returnType,
                org.springframework.http.MediaType contentType,
                Class<? extends org.springframework.http.converter.HttpMessageConverter<?>> converter,
                org.springframework.http.server.ServerHttpRequest request,
                org.springframework.http.server.ServerHttpResponse response) {
            if (request.getMethod() == org.springframework.http.HttpMethod.POST
                    && request.getURI().getPath().startsWith("/management/cluster-state/brokers/")) {
                observer.accept(body);
            }
            return body;
        }
    }

    public OpsCliGovernanceTopology() {
        this(false);
    }

    private OpsCliGovernanceTopology(boolean writeJourney) {
        this.writeJourney = writeJourney;
    }

    public static OpsCliGovernanceTopology forWriteJourney() {
        return new OpsCliGovernanceTopology(true);
    }

    public static OpsCliGovernanceTopology forWriteWithoutAuthorityJourney() {
        OpsCliGovernanceTopology topology = new OpsCliGovernanceTopology(true);
        topology.writeAuthority = false;
        return topology;
    }

    public List<AuditEvent> operationAudits(String operationId) {
        return auditEvents.stream().filter(event -> "BROKER_CLUSTER_OPERATION".equals(event.action())
                && operationId.equals(event.attributes().values().get("operationId"))).toList();
    }

    public long administratorId() {
        return jdbc.queryForObject("SELECT administrator_id FROM db_iam_administrator WHERE username='governance-admin'",
                Long.class);
    }

    public OpsCliClient loginSuccessor() throws Exception {
        jdbc.update("""
                INSERT INTO db_iam_administrator(administrator_id,username,email,password_hash,status)
                VALUES(90004,'governance-successor','successor@example.invalid',?,1)
                """, new BCryptPasswordEncoder(12).encode(password));
        jdbc.update("INSERT INTO db_iam_application_administrator_role(administrator_id,role_id) VALUES(90004,90002)");
        return login("successor@example.invalid", requestedScopes());
    }

    public OpsCliClient loginWithReadScope() throws Exception {
        return login("governance@example.invalid", "ops:broker:read");
    }
    private static final String DATA_EXCLUDES = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
            + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration";
    /**
     * 聚合测试类路径额外包含 IAM SDK；只隔离 Server 的扫描根，排除 SDK 及会重新开启宽扫描的入口。
     * 保留所有真实 Server 组件、SecurityFilterChain、配置与自动基础设施，不替换任何身份/持久化行为。
     */
    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @org.springframework.boot.autoconfigure.AutoConfigurationPackage(basePackageClasses = ImIamApplication.class)
    @ComponentScan(basePackageClasses = ImIamApplication.class, excludeFilters = {
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ImIamApplication.class),
            @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.co\\.kc\\.imchat\\.management\\.iam\\.sdk\\..*")
    })
    @ConfigurationPropertiesScan(basePackages = "com.co.kc.imchat.management.iam.infrastructure.config.properties")
    public static class IsolatedIamServer {
    }

    private final List<ConfigurableApplicationContext> contexts = new ArrayList<>();
    private final ConcurrentLinkedQueue<AuditEvent> auditEvents = new ConcurrentLinkedQueue<>();
    private final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.39"))
            .withDatabaseName("im_chat_iam").withUsername("governance").withPassword(secret());
    private final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2.0"))
            .withExposedPorts(6379);
    private final String password = secret();
    private final String webSecret = secret();
    private final String keyPassword = secret();
    private final String brokerCredential = secret();
    private final int callbackPort = availablePort();
    private final URI callbackUri = URI.create("http://127.0.0.1:" + callbackPort + "/oauth/callback");
    private Path directory;
    private Path operationDirectory;
    private String learnerMembershipEndpoint;
    private SSLContext tls;
    private URI iamOrigin;
    private URI opsOrigin;
    private BrokerManagementEndpoint brokerManagementEndpoint;
    private String brokerMembershipEndpoint;
    private ConfigurableApplicationContext iam;

    private volatile String lastEnvelopeShape = "UNOBSERVED";
    private volatile String lastQueryException = "UNOBSERVED";
    private volatile String lastSubmissionException = "UNOBSERVED";
    private final AtomicInteger lastClusterStatus = new AtomicInteger(-1);
    private final AtomicInteger lastTokenStatus = new AtomicInteger(-1);
    private String stage = "owned TLS";
    private JdbcTemplate jdbc;

    public void start() throws Exception {
        try {
            startOwnedTopology();
        } catch (Exception | AssertionError failure) {
            // SDK/SQL/HTTP 异常可能带凭据；只报告阶段与异常类型，不保留消息或 cause。
            throw sanitizedFailure("setup " + stage, failure);
        }
    }

    private void startOwnedTopology() throws Exception {
        directory = Files.createTempDirectory("im-chat-ops-governance-");
        createTrustedKeyStore();
        stage = "owned MySQL";
        mysql.start();
        stage = "owned Redis";
        redis.start();
        stage = "IAM schema";
        DriverManagerDataSource datasource = new DriverManagerDataSource(mysql.getJdbcUrl(),
                mysql.getUsername(), mysql.getPassword());
        Path root = repositoryRoot();
        ResourceDatabasePopulator schema = new ResourceDatabasePopulator(
                new FileSystemResource(root.resolve("im-management/im-iam/im-iam-server/sql/ddl.sql")),
                new FileSystemResource(root.resolve("im-management/im-iam/im-iam-server/sql/dml.sql")));
        schema.execute(datasource);
        jdbc = new JdbcTemplate(datasource);
        // 固定一次部署输入并精确注册；绑定失败直接失败，不探测回退端口。
        jdbc.update("UPDATE db_iam_oauth_client SET redirect_uris=JSON_ARRAY(?) WHERE oauth_client_id='im-ops-cli'",
                callbackUri.toString());
        jdbc.update("UPDATE db_iam_oauth_client SET client_secret_hash=? WHERE oauth_client_id='im-ops-client'",
                new BCryptPasswordEncoder(12).encode(webSecret));
        jdbc.update("""
                INSERT INTO db_iam_application_permission(permission_id,app_id,code,name,description,status)
                VALUES(90001,10004,'ops:broker:read','Broker read','governance fixture',1)
                """);

        if (writeJourney) {
            jdbc.update("""
                    INSERT INTO db_iam_application_permission(permission_id,app_id,code,name,description,status)
                    VALUES(90003,10004,'ops:broker:raft:write','Broker Raft write','governance fixture',1)
                    """);
            jdbc.update("UPDATE db_iam_oauth_client SET scopes=? WHERE oauth_client_id='im-ops-cli'",
                    "ops:broker:read ops:broker:raft:write");
        }

        Map<String, Object> iamProperties = base("governance-iam");
        int iamPort = availablePort();
        iamOrigin = URI.create("https://localhost:" + iamPort);
        configureTls(iamProperties, iamPort);
        iamProperties.put("spring.datasource.url", mysql.getJdbcUrl());
        iamProperties.put("spring.datasource.username", mysql.getUsername());
        iamProperties.put("spring.datasource.password", mysql.getPassword());
        iamProperties.put("im.iam.enabled", false);
        iamProperties.put("im.session.web.public-paths", "/login,/index.html,/assets/**,/oauth2/**,/error");
        iamProperties.put("im.iam.authorization.issuer", iamOrigin.toString());
        iamProperties.put("im.iam.authorization.key-store-location", directory.resolve("server.p12").toUri().toString());
        iamProperties.put("im.iam.authorization.key-store-password", keyPassword);
        iamProperties.put("im.iam.authorization.key-password", keyPassword);
        iamProperties.put("im.iam.authorization.key-alias", "localhost");
        iamProperties.put("im.iam.bootstrap.enabled", true);
        iamProperties.put("im.iam.bootstrap.username", "governance-admin");
        iamProperties.put("im.iam.bootstrap.email", "governance@example.invalid");
        iamProperties.put("im.iam.bootstrap.password", password);
        stage = "IAM runtime";
        iam = launch(IsolatedIamServer.class, iamProperties, null);
        // IAM 内部 SUPER_ADMIN 不自动拥有应用权限；显式持久化 Ops 应用角色与关联。
        jdbc.update("""
                INSERT INTO db_iam_application_role(role_id,app_id,code,name,type,status)
                VALUES(90002,10004,'governance-reader','Governance reader',2,1)
                """);
        jdbc.update("INSERT INTO db_iam_application_role_permission(role_id,permission_id) VALUES(90002,90001)");
        if (writeJourney && writeAuthority) {
            jdbc.update("INSERT INTO db_iam_application_role_permission(role_id,permission_id) VALUES(90002,90003)");
        }
        jdbc.update("""
                INSERT INTO db_iam_application_administrator_role(administrator_id,role_id)
                SELECT administrator_id,90002 FROM db_iam_administrator WHERE username='governance-admin'
                """);

        int boltPort = availablePort();
        String raftEndpoint = "127.0.0.1:" + availablePort();
        brokerMembershipEndpoint = raftEndpoint;
        Map<String, Object> broker = base("governance-broker");
        // 聚合测试类路径带入 Spring Security；仅隔离 Broker 非生产依赖触发的默认安全链。
        broker.put("spring.autoconfigure.exclude", DATA_EXCLUDES
                + ",org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
                + ",org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration");
        broker.put("im.audit.enabled", false);
        broker.put("im.iam.enabled", false);
        // 与现有 Broker 合约 E2E 相同的显式开发准入；独立 config name 不加载主 application.yml 的前缀。
        broker.put("spring.profiles.active", "bolt-dev");
        broker.put("im.bolt.client.allowed-peer-uri-prefixes[0]", "spiffe://im-chat/broker/");
        broker.put("im.bolt.client.allowed-peer-uri-prefixes[1]", "spiffe://im-chat/gateway/");
        broker.put("im.bolt.client.enabled", true);
        broker.put("im.bolt.server.enabled", true);
        for (String side : List.of("client", "server")) {
            broker.put("im.bolt." + side + ".mode", "DEVELOPMENT_PLAINTEXT");
            broker.put("im.bolt." + side + ".tls.enabled", false);
            broker.put("im.bolt." + side + ".development.synthetic-data", true);
        }
        broker.put("im.bolt.server.host", "127.0.0.1");
        broker.put("im.bolt.server.port", boltPort);
        broker.put("im.broker.instance.host", "127.0.0.1");
        broker.put("im.broker.instance.port", boltPort);
        broker.put("im.session.web.public-paths[0]", "/management/**");
        broker.put("im.broker.management.write-audience", "im-broker-management");
        broker.put("im.broker.management.write-token", brokerCredential);
        broker.put("im.broker.raft.cluster-id", "owned-governance-cluster");
        broker.put("im.broker.routing.allow-trusted-network-gateway-identity", true);
        broker.put("im.broker.routing.allow-trusted-network-broker-identity", true);
        broker.put("im.broker.routing.allow-trusted-network-service-identity", true);
        broker.put("im.broker.raft.node-id", BROKER_ID);
        broker.put("im.broker.raft.endpoint", raftEndpoint);
        broker.put("im.broker.raft.initial-voters[0]", raftEndpoint);
        broker.put("im.broker.raft.data-directory", directory.resolve("raft").toString());
        broker.put("im.broker.raft.bootstrap-brokers[0].broker-id", BROKER_ID);
        broker.put("im.broker.raft.bootstrap-brokers[0].route-endpoint", "127.0.0.1:" + boltPort);
        broker.put("im.broker.raft.bootstrap-brokers[0].membership-endpoint", raftEndpoint);
        broker.put("im.broker.raft.bootstrap-brokers[0].membership-role", "VOTER");
        broker.put("im.broker.raft.bootstrap-brokers[0].routing-state", "ACTIVE");
        if (writeJourney) {
            learnerMembershipEndpoint = "127.0.0.1:" + availablePort();
            broker.put("im.broker.raft.bootstrap-brokers[1].broker-id", LEARNER_ID);
            broker.put("im.broker.raft.bootstrap-brokers[1].route-endpoint", confirmationJourney
                    ? "127.0.0.1:" + availablePort() : learnerMembershipEndpoint);
            broker.put("im.broker.raft.bootstrap-brokers[1].membership-role",
                    confirmationJourney ? "LEARNER_SYNCING" : "DISCOVERED");
            if (confirmationJourney) {
                broker.put("im.broker.raft.initial-learners[0]", learnerMembershipEndpoint);
                broker.put("im.broker.raft.bootstrap-brokers[1].membership-endpoint", learnerMembershipEndpoint);
            }
            broker.put("im.broker.raft.bootstrap-brokers[1].routing-state", "INACTIVE");
            operationDirectory = Files.createTempDirectory(Path.of(System.getProperty("user.home")).toRealPath(),
                    ".im-chat-governance-operation-",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        }
        stage = "Broker runtime";
        ConfigurableApplicationContext brokerContext;
        if (followerJourney) {
            brokerContext = startFollowerBrokers(broker);
        } else {
            brokerContext = launch(ImBrokerApplication.class, broker, null);
        }
        if (confirmationJourney) {
            Map<String, Object> learner = new LinkedHashMap<>(broker);
            int learnerBoltPort = Integer.parseInt(((String) broker.get(
                    "im.broker.raft.bootstrap-brokers[1].route-endpoint")).split(":")[1]);
            learner.put("im.broker.raft.node-id", LEARNER_ID);
            learner.put("im.broker.raft.endpoint", learnerMembershipEndpoint);
            learner.put("im.broker.raft.data-directory", directory.resolve("raft-learner").toString());
            learner.put("im.bolt.server.port", learnerBoltPort);
            learner.put("im.broker.instance.port", learnerBoltPort);
            launch(ImBrokerApplication.class, learner, null);
        }
        BrokerRaftAdapter raft = brokerContext.getBean(BrokerRaftAdapter.class);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(raft.getLocalBroker().isLeader()).isTrue());
        int brokerPort = ((ServletWebServerApplicationContext) brokerContext).getWebServer().getPort();
        brokerManagementEndpoint = new BrokerManagementEndpoint(BROKER_ID,
                URI.create("http://127.0.0.1:" + brokerPort), null);

        Map<String, Object> ops = base("governance-ops");
        ops.put("spring.autoconfigure.exclude", DATA_EXCLUDES);
        int opsPort = availablePort();
        opsOrigin = URI.create("https://localhost:" + opsPort);
        configureTls(ops, opsPort);
        ops.put("im.iam.enabled", true);
        // 管理端使用真实 IAM SecurityFilterChain，不要求面向消息业务的 Gateway Header。
        ops.put("im.session.web.public-paths[0]", "/api/cli/**");
        ops.put("im.iam.issuer", iamOrigin.toString());
        ops.put("im.iam.application.key", "imOps");
        ops.put("im.iam.application.clients.web.client-id", "im-ops-client");
        ops.put("im.iam.application.clients.web.client-secret", webSecret);
        ops.put("im.iam.application.clients.web.redirect-uri", opsOrigin + "/iam/callback");
        ops.put("im.iam.application.clients.web.post-logout-redirect-uri", opsOrigin + "/");
        ops.put("im.iam.application.session.encryption-key", secret());
        // 独立配置名不读取 Ops application.yml；显式保留其必填有界查询执行器输入。
        ops.put("im.ops.query.threads", 8);
        ops.put("im.ops.query.queue-capacity", 128);
        ops.put("im.ops.cli.client-ids", "im-ops-cli");
        ops.put("im.ops.cli.scopes", requestedScopes().replace(' ', ','));
        ops.put("im.ops.broker.management-token", brokerCredential);
        stage = "Ops runtime";
        launch(ImOpsApplication.class, ops, brokerPort);
    }

    private ConfigurableApplicationContext startFollowerBrokers(Map<String, Object> template) throws Exception {
        List<String> voters = List.of("127.0.0.1:" + availablePort(),
                "127.0.0.1:" + availablePort(), "127.0.0.1:" + availablePort());
        List<Integer> boltPorts = List.of(availablePort(), availablePort(), availablePort());
        for (int local = 0; local < voters.size(); local++) {
            Map<String, Object> properties = new LinkedHashMap<>(template);
            properties.keySet().removeIf(key -> key.startsWith("im.broker.raft.bootstrap-brokers[")
                    || key.startsWith("im.broker.raft.initial-voters["));
            properties.put("im.broker.raft.node-id", voterId(local));
            properties.put("im.broker.raft.endpoint", voters.get(local));
            properties.put("im.broker.raft.data-directory", directory.resolve("raft-" + local).toString());
            properties.put("im.bolt.server.port", boltPorts.get(local));
            properties.put("im.broker.instance.port", boltPorts.get(local));
            for (int member = 0; member < voters.size(); member++) {
                properties.put("im.broker.raft.initial-voters[" + member + "]", voters.get(member));
                String prefix = "im.broker.raft.bootstrap-brokers[" + member + "].";
                properties.put(prefix + "broker-id", voterId(member));
                properties.put(prefix + "route-endpoint", "127.0.0.1:" + boltPorts.get(member));
                properties.put(prefix + "membership-endpoint", voters.get(member));
                properties.put(prefix + "membership-role", "VOTER");
                properties.put(prefix + "routing-state", "ACTIVE");
            }
            String prefix = "im.broker.raft.bootstrap-brokers[3].";
            properties.put(prefix + "broker-id", LEARNER_ID);
            properties.put(prefix + "route-endpoint", learnerMembershipEndpoint);
            properties.put(prefix + "membership-role", "DISCOVERED");
            properties.put(prefix + "routing-state", "INACTIVE");
            brokerContexts.add(launch(ImBrokerApplication.class, properties, null));
        }
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(brokerContexts.stream().filter(context -> context.getBean(BrokerRaftAdapter.class)
                        .getLocalBroker().isLeader()).count()).isEqualTo(1));
        ConfigurableApplicationContext leader = brokerContexts.stream().filter(context ->
                context.getBean(BrokerRaftAdapter.class).getLocalBroker().isLeader()).findFirst().orElseThrow();
        String leaderPeer = leader.getBean(BrokerRaftAdapter.class).getLocalRaftPeerId();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> brokerContexts.forEach(context ->
                assertThat(context.getBean(BrokerRaftAdapter.class).getLeaderEndpoint()).isEqualTo(leaderPeer)));
        List<ServiceInstance> discovered = new ArrayList<>();
        for (ConfigurableApplicationContext context : brokerContexts) {
            DefaultServiceInstance captured = captureOfficialRegistration(context);
            if (context == leader) {
                discovered.add(captured);
            } else {
                discovered.addFirst(captured);
            }
        }
        registeredInstances = List.copyOf(discovered);
        if ("MISSING".equals(discoveryMutation)) {
            // 仅变异已捕获的发现边界 metadata，不改真实 Node 身份或返回 hint。
            discovered.stream().filter(instance -> leaderPeer.equals(instance.getMetadata().get("raft-peer-id")))
                    .forEach(instance -> instance.getMetadata().remove("raft-peer-id"));
        } else if ("CONFLICT".equals(discoveryMutation)) {
            ServiceInstance officialLeader = discovered.stream().filter(instance ->
                    leaderPeer.equals(instance.getMetadata().get("raft-peer-id"))).findFirst().orElseThrow();
            DefaultServiceInstance conflict = new DefaultServiceInstance("discovery-conflict", "im-broker",
                    officialLeader.getHost(), officialLeader.getPort(), false);
            conflict.getMetadata().putAll(officialLeader.getMetadata());
            conflict.getMetadata().put("management-port", Integer.toString(
                    ((ServletWebServerApplicationContext) brokerContexts.stream().filter(broker -> broker != leader)
                            .findFirst().orElseThrow()).getWebServer().getPort()));
            // 只在发现边界构造相同官方 PeerId 的不同真实管理 origin，hint 和 Node 保持原样。
            discovered.add(conflict);
            registeredInstances = List.copyOf(discovered);
        }
        brokerMembershipEndpoint = leaderPeer;
        return leader;
    }

    private String voterId(int index) {
        return index == 0 ? BROKER_ID : BROKER_ID + "-" + index;
    }

    private DefaultServiceInstance captureOfficialRegistration(ConfigurableApplicationContext context) throws Exception {
        int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
        NacosDiscoveryProperties discovery = new NacosDiscoveryProperties();
        discovery.setService("im-broker");
        discovery.setIp("127.0.0.1");
        discovery.setPort(context.getEnvironment().getRequiredProperty("im.bolt.server.port", Integer.class));
        discovery.getMetadata().put("management-host", "127.0.0.1");
        discovery.getMetadata().put("management-port", Integer.toString(port));
        NacosRegistration registration = new NacosRegistration(List.copyOf(
                context.getBeansOfType(NacosRegistrationCustomizer.class).values()), discovery, context);
        registration.init();
        NamingService naming = mock(NamingService.class);
        NacosServiceManager manager = mock(NacosServiceManager.class);
        when(manager.getNamingService()).thenReturn(naming);
        when(manager.getNamingService(any())).thenReturn(naming);
        new NacosServiceRegistry(manager, discovery).register(registration);
        ArgumentCaptor<Instance> captured = ArgumentCaptor.forClass(Instance.class);
        verify(naming).registerInstance(eq("im-broker"), eq(discovery.getGroup()), captured.capture());
        Instance instance = captured.getValue();
        assertThat(instance.getMetadata().get("raft-peer-id"))
                .isEqualTo(context.getBean(BrokerRaftAdapter.class).getLocalRaftPeerId());
        DefaultServiceInstance result = new DefaultServiceInstance(context.getEnvironment()
                .getRequiredProperty("im.broker.raft.node-id"), "im-broker", instance.getIp(), instance.getPort(), false);
        result.getMetadata().putAll(instance.getMetadata());
        return result;
    }

    public URI opsOrigin() {
        return opsOrigin;
    }

    public String learnerMembershipEndpoint() {
        return learnerMembershipEndpoint;
    }

    public Path operationRecordPath() {
        return operationDirectory.resolve("request.json");
    }

    private String requestedScopes() {
        return writeJourney ? "ops:broker:read ops:broker:raft:write" : "ops:broker:read";
    }

    public String brokerMembershipEndpoint() {
        return brokerMembershipEndpoint;
    }

    public BrokerManagementEndpoint brokerManagementEndpoint() {
        return brokerManagementEndpoint;
    }

    public HttpBrokerManagementClient authorizedBrokerClient() {
        return brokerClient(brokerCredential);
    }

    public HttpBrokerManagementClient unauthorizedBrokerClient() {
        return brokerClient(brokerCredential + "-invalid");
    }

    private HttpBrokerManagementClient brokerClient(String credential) {
        OpsBrokerProperties properties = new OpsBrokerProperties();
        properties.setManagementToken(credential);
        RestClient restClient = new OpsHttpBeans().brokerRestClient(RestClient.builder(), properties);
        return new HttpBrokerManagementClient(restClient, properties);
    }

    public enum JourneyStage {
        LOGIN_SESSION, AUTHORIZE_LOGIN_REDIRECT, LOGIN_REDIRECT_TARGET, LOGIN_PAGE,
        LOGIN_CSRF_COOKIE, LOGIN_FORM_POST, AUTHORIZE_CODE_REDIRECT, CALLBACK_TARGET,
        CALLBACK_DELIVERY, TOKEN_EXCHANGE, INITIAL_QUERY, INITIAL_PROJECTION_ASSERT,
        CLIENT_DISABLE, COMMITTED_DISABLE_ASSERT, REJECTED_READ, REJECTED_STATUS_ASSERT, CLEANUP,
        BROKER_WRITE_BASELINE, BROKER_INVALID_CREDENTIAL, BROKER_INVALID_UNCHANGED,
        BROKER_VALID_CREDENTIAL, BROKER_VALID_UNCHANGED,
        CLI_WRITE_BASELINE, CLI_SUBMIT, CLI_RECORD_ASSERT, CLI_OPERATION_QUERY,
        CLI_LEARNER_PROJECTION, CLI_EXPLICIT_RETRY, CLI_RETRY_UNCHANGED
    }

    public void enterStage(JourneyStage next) {
        stage = next.name();
    }

    public AssertionError sanitizeCurrentFailure(Throwable failure) {
        return sanitizedFailure(stage + (JourneyStage.TOKEN_EXCHANGE.name().equals(stage)
                ? "[HTTP=" + lastTokenStatus.get() + "]" : ""), failure);
    }

    public void assertHttpStatus(int actual, int expected) {
        if (actual != expected) {
            // 状态数值限制在标准 HTTP 范围，诊断不包含响应内容。
            int boundedActual = actual >= 100 && actual <= 599 ? actual : -1;
            int boundedExpected = expected >= 100 && expected <= 599 ? expected : -1;
            throw new SanitizedJourneyFailure("Governance journey failed at " + stage
                    + " (HTTP actual=" + boundedActual + ", expected=" + boundedExpected + ")");
        }
    }

    public OpsCliClient login() throws Exception {
        return login("governance@example.invalid", requestedScopes());
    }

    private OpsCliClient login(String email, String scopes) throws Exception {
        enterStage(JourneyStage.LOGIN_SESSION);
        CliLoginSession session = CliLoginSession.start();
        HttpClient clientHttp = new StatusObservingHttpClient(http(null));
        OpsCliClient client = new OpsCliClient(iamOrigin, opsOrigin, "im-ops-cli", clientHttp);
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient browser = http(cookies);
        URI authorize = iamOrigin.resolve("/oauth2/authorize?response_type=code&client_id=im-ops-cli"
                + "&redirect_uri=" + encode(callbackUri.toString()) + "&scope=" + encode(scopes)
                + "&state=" + encode(session.state()) + "&code_challenge=" + encode(session.challenge())
                + "&code_challenge_method=S256");
        try (LoopbackAuthorizationCallback callback = new LoopbackAuthorizationCallback(callbackPort);
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<String> code = executor.submit(() -> callback.awaitAuthorizationCode(session));
            try {
                enterStage(JourneyStage.AUTHORIZE_LOGIN_REDIRECT);
                HttpResponse<String> authorization = browser.send(HttpRequest.newBuilder(authorize)
                        .timeout(Duration.ofSeconds(10)).header("Accept", "text/html").GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertHttpStatus(authorization.statusCode(), 302);
                enterStage(JourneyStage.LOGIN_REDIRECT_TARGET);
                URI login = iamOrigin.resolve(authorization.headers().firstValue("Location").orElseThrow());
                assertThat(login.getPath()).isEqualTo("/login");
                enterStage(JourneyStage.LOGIN_PAGE);
                HttpResponse<Void> loginPage = browser.send(HttpRequest.newBuilder(login)
                        .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.discarding());
                assertHttpStatus(loginPage.statusCode(), 200);
                enterStage(JourneyStage.LOGIN_CSRF_COOKIE);
                String csrf = cookies.getCookieStore().getCookies().stream()
                        .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                        .findFirst().orElseThrow(() -> new AssertionError("IAM did not issue login CSRF cookie")).getValue();
                // Spring 表单参数名是 username，但 IAM 认证应用服务按管理员 email 查找账号。
                String form = "username=" + encode(email)
                        + "&password=" + encode(password) + "&_csrf=" + encode(csrf);
                enterStage(JourneyStage.LOGIN_FORM_POST);
                HttpResponse<Void> loggedIn = browser.send(HttpRequest.newBuilder(iamOrigin.resolve("/login"))
                        .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.discarding());
                assertHttpStatus(loggedIn.statusCode(), 302);
                URI postLoginDestination = iamOrigin.resolve(loggedIn.headers().firstValue("Location").orElseThrow());
                if ("/login".equals(postLoginDestination.getPath()) || hasQueryKey(postLoginDestination, "error")) {
                    throw redirectClassification(postLoginDestination, authorize);
                }
                enterStage(JourneyStage.AUTHORIZE_CODE_REDIRECT);
                HttpResponse<Void> authorized = browser.send(HttpRequest.newBuilder(authorize)
                        .timeout(Duration.ofSeconds(10)).header("Accept", "text/html").GET().build(),
                        HttpResponse.BodyHandlers.discarding());
                assertHttpStatus(authorized.statusCode(), 302);
                enterStage(JourneyStage.CALLBACK_TARGET);
                URI destination = URI.create(authorized.headers().firstValue("Location").orElseThrow());
                if (!callbackUri.getScheme().equals(destination.getScheme())
                        || !callbackUri.getAuthority().equals(destination.getAuthority())
                        || !callbackUri.getPath().equals(destination.getPath())
                        || hasQueryKey(destination, "error")) {
                    throw redirectClassification(destination, callbackUri);
                }
                enterStage(JourneyStage.CALLBACK_DELIVERY);
                HttpResponse<Void> delivered = browser.send(HttpRequest.newBuilder(destination)
                        .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.discarding());
                assertHttpStatus(delivered.statusCode(), 200);
                enterStage(JourneyStage.TOKEN_EXCHANGE);
                client.exchangeAuthorizationCode(session, code.get(10, TimeUnit.SECONDS), callbackUri);
                return client;
            } finally {
                callback.close();
                code.cancel(true);
                executor.shutdownNow();
            }
        }
    }

    private AssertionError redirectClassification(URI actual, URI expected) {
        return new SanitizedJourneyFailure("Governance journey failed at " + stage
                + " (redirect schemeEqual=" + expected.getScheme().equals(actual.getScheme())
                + ", authorityEqual=" + expected.getAuthority().equals(actual.getAuthority())
                + ", pathEqual=" + expected.getPath().equals(actual.getPath())
                + ", loginPath=" + "/login".equals(actual.getPath())
                + ", authorizePath=" + "/oauth2/authorize".equals(actual.getPath())
                + ", errorKey=" + hasQueryKey(actual, "error")
                + ", loginErrorTrue=" + ("/login".equals(actual.getPath()) && hasLoginErrorTrue(actual)) + ")");
    }

    private static boolean hasQueryKey(URI uri, String key) {
        String query = uri.getRawQuery();
        if (query == null) {
            return false;
        }
        for (String parameter : query.split("&")) {
            if (parameter.equals(key) || parameter.startsWith(key + "=")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLoginErrorTrue(URI uri) {
        String query = uri.getRawQuery();
        if (query == null) {
            return false;
        }
        for (String parameter : query.split("&")) {
            if (parameter.equals("error=true")) {
                return true;
            }
        }
        return false;
    }

    public void disablePublicClient() {
        enterStage(JourneyStage.CLIENT_DISABLE);
        iam.getBean(OAuthClientAppService.class).disable(new OAuthClientDisableCmd("im-ops-cli"));
        enterStage(JourneyStage.COMMITTED_DISABLE_ASSERT);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM db_iam_oauth_client WHERE oauth_client_id='im-ops-cli'", Integer.class))
                .as("Transactional disable committed in owned MySQL").isEqualTo(2);
    }

    private Map<String, Object> base(String name) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.config.name", name);
        properties.put("spring.application.name", name);
        properties.put("server.address", "127.0.0.1");
        properties.put("server.port", 0);
        properties.put("spring.main.banner-mode", "off");
        properties.put("logging.level.root", "OFF");
        properties.put("spring.cloud.nacos.discovery.enabled", false);
        properties.put("spring.cloud.nacos.config.enabled", false);
        properties.put("spring.cloud.discovery.enabled", false);
        properties.put("spring.cloud.discovery.client.composite-indicator.enabled", false);
        properties.put("spring.cloud.stream.enabled", false);
        properties.put("im.dubbo.enabled", false);
        properties.put("dubbo.enabled", false);
        properties.put("dubbo.registry.address", "N/A");
        properties.put("im.dubbo.registry.address", "N/A");
        properties.put("im.datasource.sharding.enabled", false);
        properties.put("spring.data.redis.host", redis.getHost());
        properties.put("spring.data.redis.port", redis.getMappedPort(6379));
        properties.put("im.identity.snowflake.mode", "STATIC");
        properties.put("im.identity.snowflake.data-center-id", 0);
        properties.put("im.identity.snowflake.machine-id", 1);
        properties.put("im.audit.enabled", true);
        properties.put("im.audit.transport", "KAFKA");
        properties.put("jetcache.remote.default.type", "mock");
        return properties;
    }

    private ConfigurableApplicationContext launch(Class<?> source, Map<String, Object> properties, Integer brokerPort) {
        SpringApplication application = new SpringApplication(source);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.setDefaultProperties(properties);
        application.addInitializers(context -> {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("owned-governance", properties));
            GenericApplicationContext beans = (GenericApplicationContext) context;
            beans.registerBean("governanceAuditReceiver", AuditTransport.class, () -> auditEvents::add);
            if (source == ImBrokerApplication.class) {
                if (recoveryOperationId != null) {
                    beans.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {
                        @Override
                        public Object postProcessAfterInitialization(Object bean, String beanName) {
                            if (bean instanceof BrokerRaftAdapter adapter) {
                                BrokerRaftAdapter controlled = spy(adapter);
                                doAnswer(call -> {
                                    BrokerClusterOperation operation = call.getArgument(0);
                                    if (recoveryPaused.get() && recoveryOperationId.equals(operation.id())) {
                                        deferredCompletions.incrementAndGet();
                                        return CompletableFuture.completedStage(BrokerClusterConsensusResult.unavailable(
                                                null, "terminal submission deferred by recovery fixture"));
                                    }
                                    return call.callRealMethod();
                                }).when(controlled).completeMembershipOperation(any());
                                return controlled;
                            }
                            if (bean instanceof BrokerMembershipAppService service) {
                                BrokerMembershipAppService controlled = spy(service);
                                doAnswer(call -> {
                                    recoveryCalls.incrementAndGet();
                                    return call.callRealMethod();
                                }).when(controlled).resume(eq(recoveryOperationId));
                                return controlled;
                            }
                            if (bean instanceof BrokerMembershipScheduler scheduler) {
                                BrokerMembershipScheduler controlled = spy(scheduler);
                                doAnswer(call -> recoveryPaused.get() ? null : call.callRealMethod())
                                        .when(controlled).reconcile();
                                return controlled;
                            }
                            return bean;
                        }
                    });
                }
                beans.registerBean("governanceMessageService", MessageService.class, () -> mock(MessageService.class));
                if (followerJourney) {
                    beans.registerBean("governanceResponseObserver", GovernanceResponseObserver.class,
                            () -> new GovernanceResponseObserver(body -> {
                                JsonNode envelope = new ObjectMapper().valueToTree(body);
                                JsonNode result = body instanceof com.co.kc.imchat.common.model.io.HttpResult<?>
                                        ? envelope.path("data") : envelope;
                                String hint = result.path("leaderEndpoint").asText();
                                boolean matches = registeredInstances.stream().anyMatch(instance ->
                                        hint.equals(instance.getMetadata().get("raft-peer-id"))
                                                && brokerContexts.stream().anyMatch(broker ->
                                                instance.getInstanceId().equals(broker.getEnvironment().getProperty("im.broker.raft.node-id"))
                                                        && broker.getBean(BrokerRaftAdapter.class).getLocalBroker().isLeader()));
                                responseObservations.put(properties.get("im.broker.raft.node-id").toString(),
                                        new ResponseObservation("NOT_LEADER".equals(result.path("status").asText()), matches));
                            }));
                }
                if (writeJourney) {
                    beans.registerBean("governanceNetworkWriteObserver", FilterRegistrationBean.class, () -> {
                        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
                        registration.setUrlPatterns(List.of("/management/cluster-state/brokers/*"));
                        registration.setFilter(new OncePerRequestFilter() {
                            @Override
                            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                            FilterChain chain) throws ServletException, IOException {
                                if (!"POST".equals(request.getMethod())
                                        || !request.getRequestURI().startsWith("/management/cluster-state/brokers/")) {
                                    chain.doFilter(request, response);
                                    return;
                                }
                                boolean leaderAtReceipt = context.getBean(BrokerRaftAdapter.class)
                                        .getLocalBroker().isLeader();
                                ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);
                                chain.doFilter(wrapped, response);
                                networkWrites.add(new NetworkWrite(properties.get("im.broker.raft.node-id").toString(),
                                        request.getRequestURI(), wrapped.getContentAsString(),
                                        request.getHeader("X-Im-Scopes"), request.getHeader("Idempotency-Key"),
                                        request.getHeader("If-Match-Epoch"), leaderAtReceipt,
                                        false, false, response.getStatus()));
                            }
                        });
                        return registration;
                    });
                }
            }
            if (source == ImOpsApplication.class) {
                beans.registerBean("governanceQueryExceptionObserver", QueryExceptionObserver.class,
                        QueryExceptionObserver::new);
                beans.registerBean("iamRestClient", RestClient.class, () -> {
                    JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http(null));
                    requests.setReadTimeout(Duration.ofSeconds(10));
                    return RestClient.builder().baseUrl(iamOrigin.toString()).requestFactory(requests).build();
                });
                DefaultServiceInstance instance = new DefaultServiceInstance(BROKER_ID, "im-broker", "127.0.0.1", brokerPort, false);
                instance.getMetadata().putAll(Map.of("management-host", "127.0.0.1", "management-port", brokerPort.toString()));
                beans.registerBean("governanceDiscovery", DiscoveryClient.class, () -> new DiscoveryClient() {
                    public String description() { return "task-owned trusted endpoint"; }
                    public List<ServiceInstance> getInstances(String serviceId) {
                        return "im-broker".equals(serviceId)
                                ? (followerJourney ? registeredInstances : List.of(instance)) : List.of();
                    }
                    public List<String> getServices() { return List.of("im-broker"); }
                });
            }
        });
        ConfigurableApplicationContext context = application.run();
        contexts.add(context);
        return context;
    }

    private void configureTls(Map<String, Object> properties, int port) {
        properties.put("server.port", port);
        properties.put("server.ssl.enabled", true);
        properties.put("server.ssl.key-store", directory.resolve("server.p12").toUri().toString());
        properties.put("server.ssl.key-store-password", keyPassword);
        properties.put("server.ssl.key-store-type", "PKCS12");
        properties.put("server.ssl.key-alias", "localhost");
    }

    public int lastClusterStatus() {
        return lastClusterStatus.get();
    }

    /** 只记录实际响应状态；不读取、复制或输出 Authorization Header、Token 或响应体。 */
    private final class StatusObservingHttpClient extends HttpClient {
        private final HttpClient delegate;

        private StatusObservingHttpClient(HttpClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() { return delegate.cookieHandler(); }
        @Override
        public Optional<Duration> connectTimeout() { return delegate.connectTimeout(); }
        @Override
        public Redirect followRedirects() { return delegate.followRedirects(); }
        @Override
        public Optional<ProxySelector> proxy() { return delegate.proxy(); }
        @Override
        public SSLContext sslContext() { return delegate.sslContext(); }
        @Override
        public SSLParameters sslParameters() { return delegate.sslParameters(); }
        @Override
        public Optional<Authenticator> authenticator() { return delegate.authenticator(); }
        @Override
        public Version version() { return delegate.version(); }
        @Override
        public Optional<Executor> executor() { return delegate.executor(); }

        private <T> HttpResponse.BodyHandler<T> observe(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            boolean clusterRead = request.uri().getAuthority().equals(opsOrigin.getAuthority())
                    && request.uri().getPath().equals("/api/cli/cluster-state");
            boolean tokenExchange = request.uri().getAuthority().equals(iamOrigin.getAuthority())
                    && request.uri().getPath().equals("/oauth2/token");
            if (clusterRead) {
                lastClusterStatus.set(-1);
            }
            return information -> {
                if (tokenExchange) {
                    lastTokenStatus.set(information.statusCode());
                }
                if (clusterRead) {
                    lastClusterStatus.set(information.statusCode());
                }
                return handler.apply(information);
            };
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
                throws IOException, InterruptedException {
            return delegate.send(request, observe(request, handler));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            return delegate.sendAsync(request, observe(request, handler)).thenApply(response -> {
                observeClusterEnvelope(request, response);
                return response;
            });
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushHandler) {
            return delegate.sendAsync(request, observe(request, handler), pushHandler);
        }
    }

    private void observeClusterEnvelope(HttpRequest request, HttpResponse<?> response) {
        if (!request.uri().getAuthority().equals(opsOrigin.getAuthority())
                || !request.uri().getPath().equals("/api/cli/cluster-state")
                || !(response.body() instanceof byte[] bytes)) {
            return;
        }
        try {
            JsonNode envelope = new ObjectMapper().readTree(bytes);
            lastEnvelopeShape = "object=" + (envelope != null && envelope.isObject())
                    + ", codeIntegral=" + (envelope != null && envelope.path("code").isIntegralNumber())
                    + ", codeZero=" + (envelope != null && envelope.path("code").isIntegralNumber()
                            && envelope.path("code").canConvertToInt() && envelope.path("code").intValue() == 0)
                    + ", dataObject=" + (envelope != null && envelope.path("data").isObject())
                    + ", topLevelSourceBroker=" + (envelope != null && envelope.has("sourceBroker"));
        } catch (IOException failure) {
            lastEnvelopeShape = "INVALID_JSON";
        }
    }

    private final class QueryExceptionObserver implements HandlerExceptionResolver, Ordered {
        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
                                             Object handler, Exception failure) {
            boolean clusterRead = "GET".equals(request.getMethod())
                    && "/api/cli/cluster-state".equals(request.getRequestURI());
            boolean learnerSubmission = "POST".equals(request.getMethod())
                    && "/api/cli/brokers/governance-learner/raft/add-learner".equals(request.getRequestURI());
            if (clusterRead || learnerSubmission) {
                StringBuilder diagnostic = new StringBuilder();
                Throwable current = failure;
                for (int depth = 0; current != null && depth < 8; depth++) {
                    if (depth > 0) {
                        diagnostic.append(" -> ");
                    }
                    diagnostic.append(diagnosticIdentifier(current.getClass().getSimpleName()));
                    if (current instanceof RestClientResponseException httpFailure) {
                        int status = httpFailure.getStatusCode().value();
                        diagnostic.append("[HTTP=")
                                .append(status >= 100 && status <= 599 ? status : -1).append(']');
                    }
                    if (current instanceof org.springframework.web.client.RestClientException) {
                        diagnostic.append("[emptyResponse=")
                                .append("Broker returned an empty response".equals(current.getMessage()))
                                .append(",conversionFrame=").append(java.util.Arrays.stream(current.getStackTrace())
                                        .anyMatch(frame -> frame.getClassName().contains("DefaultRestClient")
                                                && frame.getMethodName().contains("readWithMessageConverters")))
                                .append(']');
                    }
                    current = current.getCause();
                }
                if (learnerSubmission) {
                    lastSubmissionException = diagnostic.toString();
                } else {
                    lastQueryException = diagnostic.toString();
                }
            }
            // 仅观察类型与有界状态，继续交给原有安全链和 ErrorAdvice 处理。
            return null;
        }
    }

    public AssertionError submissionAssertionFailure(JsonNode submitted, String expectedOperationId) {
        String status = submitted.path("status").isTextual() ? submitted.path("status").textValue() : "";
        return new SanitizedJourneyFailure("Governance journey failed at " + stage
                + " (idMatches=" + expectedOperationId.equals(submitted.path("operationId").textValue())
                + ", statusApplied=" + "APPLIED".equals(status)
                + ", statusUnknown=" + "UNKNOWN".equals(status)
                + ", statusRejected=" + "REJECTED".equals(status)
                + ", statusConflict=" + "CONFLICT".equals(status)
                + ", statusNotLeader=" + "NOT_LEADER".equals(status)
                + ", statusUnavailable=" + "UNAVAILABLE".equals(status)
                + ", statusDuplicate=" + "DUPLICATE".equals(status)
                + "; submissionException=" + lastSubmissionException
                + "; networkWriteCount=" + networkWrites.size()
                + "; firstWriteObserved=" + !networkWrites.isEmpty()
                + "; firstWriteLeader=" + (!networkWrites.isEmpty() && networkWrites.peek().leaderAtReceipt())
                + "; firstHttpStatus=" + (networkWrites.isEmpty() ? -1 : networkWrites.peek().responseStatus())
                + "; firstResponseNotLeader=" + (!networkWrites.isEmpty() && networkWrites().getFirst().notLeaderResponse())
                + "; firstHintOfficialMatch=" + (!networkWrites.isEmpty() && networkWrites().getFirst().hintMatchesOfficialLeader())
                + "; diagnosticUnavailable=" + "membership configuration diagnostic unavailable".equals(submitted.path("message").asText())
                + "; outcomeUnknown=" + "membership outcome unknown".equals(submitted.path("message").asText())
                + "; membershipAdmissionClosed=" + "membership workflow admission closed or full".equals(submitted.path("message").asText()) + ")");
    }

    public AssertionError initialQueryFailure(IOException failure) {
        String classification = switch (failure.getMessage() == null ? "" : failure.getMessage()) {
            case "Invalid Ops response" -> "INVALID_OPS_RESPONSE";
            case "HTTP request failed" -> "HTTP_REQUEST_FAILED";
            case "HTTP response exceeds 1 MiB" -> "RESPONSE_LIMIT";
            default -> "OTHER_IO";
        };
        return new SanitizedJourneyFailure("Governance journey failed at " + stage
                + " (classification=" + classification + "; envelopeShape=" + lastEnvelopeShape
                + "; queryException=" + lastQueryException + ")");
    }

    private HttpClient http(CookieManager cookies) {
        HttpClient.Builder builder = HttpClient.newBuilder().sslContext(tls)
                .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER);
        if (cookies != null) {
            builder.cookieHandler(cookies).version(HttpClient.Version.HTTP_1_1);
        }
        return builder.build();
    }

    private void createTrustedKeyStore() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "localhost", "-keyalg", "RSA", "-keysize", "2048", "-storetype", "PKCS12",
                "-keystore", directory.resolve("server.p12").toString(), "-storepass:env", "GOVERNANCE_KEY_PASSWORD",
                "-keypass:env", "GOVERNANCE_KEY_PASSWORD", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost",
                "-validity", "2", "-noprompt");
        builder.environment().put("GOVERNANCE_KEY_PASSWORD", keyPassword);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        try {
            assertThat(process.waitFor(15, TimeUnit.SECONDS)).as("Owned TLS key generation completes").isTrue();
            assertThat(process.exitValue()).as("Owned TLS key generation succeeds").isZero();
        } finally {
            process.destroyForcibly();
        }
        KeyStore server = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(directory.resolve("server.p12"))) {
            server.load(input, keyPassword.toCharArray());
        }
        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
        trust.load(null, null);
        trust.setCertificateEntry("localhost", server.getCertificate("localhost"));
        TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trust);
        tls = SSLContext.getInstance("TLS");
        tls.init(null, managers.getTrustManagers(), null);
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.isRegularFile(candidate.resolve("AGENTS.md"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("Repository root not found");
        }
        // 最近的 AGENTS 可能属于 im-test，继续定位根模块。
        while (!Files.isDirectory(candidate.resolve("im-management"))) {
            candidate = candidate.getParent();
            if (candidate == null) {
                throw new IllegalStateException("Repository root not found");
            }
        }
        return candidate;
    }

    private static String secret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static int availablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot select owned deployment port");
        }
    }

    private static final class SanitizedJourneyFailure extends AssertionError {
        private SanitizedJourneyFailure(String message) {
            super(message);
        }
    }

    public static AssertionError sanitizedFailure(String stage, Throwable failure) {
        if (failure instanceof SanitizedJourneyFailure sanitized) {
            return sanitized;
        }
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        StringBuilder diagnostic = new StringBuilder();
        Throwable current = failure;
        // 只保留最多八层类型及 Spring Bean 名；绝不读取异常消息或附加原始 cause。
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (depth > 0) {
                diagnostic.append(" -> ");
            }
            diagnostic.append(diagnosticIdentifier(current.getClass().getSimpleName()));
            if (current instanceof org.springframework.beans.factory.BeanCreationException beanFailure) {
                diagnostic.append("[bean=").append(diagnosticIdentifier(beanFailure.getBeanName())).append(']');
            }
            current = current.getCause();
        }
        return new SanitizedJourneyFailure("Governance journey failed at " + stage + " ("
                + diagnostic + "; sensitive details suppressed)");
    }

    private static String diagnosticIdentifier(String value) {
        if (value == null) {
            return "unknown";
        }
        String bounded = value.substring(0, Math.min(value.length(), 96));
        return bounded.replaceAll("[^A-Za-z0-9_.$-]", "_");
    }

    @Override
    public void close() {
        boolean failed = false;
        for (int index = contexts.size() - 1; index >= 0; index--) {
            try {
                contexts.get(index).close();
            } catch (Exception failure) {
                failed = true;
            }
        }
        for (AutoCloseable container : List.of(redis, mysql)) {
            try {
                container.close();
            } catch (Exception failure) {
                failed = true;
            }
        }
        if (operationDirectory != null) {
            // 仅删除本 fixture 创建的精确记录和目录，不遍历或改动用户 home 的其他内容。
            try {
                Files.deleteIfExists(operationDirectory.resolve("request.json"));
                Files.deleteIfExists(operationDirectory.resolve("confirmation.json"));
                Files.deleteIfExists(operationDirectory);
            } catch (IOException failure) {
                failed = true;
            }
        }
        if (directory != null) {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException failure) {
                        failed = true;
                    }
                }
            } catch (IOException failure) {
                failed = true;
            }
        }
        if (failed) {
            throw new AssertionError("Owned governance resource cleanup incomplete; sensitive details suppressed");
        }
    }
}
