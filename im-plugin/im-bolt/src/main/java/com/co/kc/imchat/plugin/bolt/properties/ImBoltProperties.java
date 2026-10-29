package com.co.kc.imchat.plugin.bolt.properties;

import com.co.kc.imchat.plugin.bolt.model.enums.BoltTransportMode;
import lombok.Data;
import lombok.ToString;
import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bolt 客户端与服务端的类型化配置，绑定 im.bolt 下的传输、安全和资源选项。
 */
@Data
@ConfigurationProperties(prefix = "im.bolt")
public class ImBoltProperties {
    /* 出站客户端配置，默认不启用。 */
    private Client client = new Client();

    /* 入站服务端配置，默认不启用。 */
    private Server server = new Server();

    /**
     * 受控客户端的传输模式、目标身份命名空间和有界资源配置。
     */
    @Data
    public static class Client {
        /* 是否装配默认受控客户端。 */
        private boolean enabled;
        /* 客户端 TLS 材料与选项，MTLS 模式要求显式启用。 */
        private Tls tls = new Tls();
        /* 明文开发模式的显式安全声明。 */
        private Development development = new Development();
        /* 固定传输模式，默认使用双向 TLS，不因认证失败回退明文。 */
        private BoltTransportMode mode = BoltTransportMode.MTLS;
        /* 允许的对端身份 URI 前缀；启用时必填，须以 / 结尾且互不重叠，不替代完整身份匹配或业务角色授权。 */
        private List<String> allowedPeerUriPrefixes = List.of();
        /* 同一目标共享的连接创建总预算，包含连接和认证，不是单个调用者的等待预算。 */
        private Duration connectionCreationTimeout = Duration.ofSeconds(3);
        /* 同时执行共享连接创建任务的线程数。 */
        private int creationThreads = 2;
        /* 等待执行的共享连接创建任务容量；满额时拒绝，不无限排队。 */
        private int creationQueueCapacity = 16;
        /* 按地址与完整预期身份隔离的目标连接池数量上限。 */
        private int maxTargetPools = 64;
        /* 每目标连接数量；当前仅允许配置为 1。 */
        private int maxConnectionsPerTarget = 1;
        /* 等待获取连接的调用者数量上限；调用者停止等待不代表底层工作已经结束。 */
        private int maxPendingCallers = 64;
        /* 已准入且尚未终结的 RPC 数量上限。 */
        private int maxInflightRpcs = 64;
        /* 是否启用后台重连；当前必须为 false，开启时校验拒绝。 */
        private boolean backgroundReconnectEnabled;
        /* 是否启用连接预热；当前必须为 false，开启时校验拒绝。 */
        private boolean connectionWarmupEnabled;

        /**
         * 校验身份前缀、模式前提及资源上界；无效配置抛出参数异常。
         * 开发 profile 与实际网络地址约束由装配和目标准入流程另行校验。
         */
        public void validateTypedPolicy() {
            AssertUtils.argNotNull("Bolt client mode is required", mode);
            AssertUtils.argNotEmpty("Bolt peer URI prefixes are required", allowedPeerUriPrefixes);
            for (String prefix : allowedPeerUriPrefixes) {
                AssertUtils.argNotBlank("Bolt peer URI prefix is required", prefix);
                URI uri = URI.create(prefix);
                AssertUtils.argTrue("Bolt peer URI prefix requires an absolute URI and node boundary",
                        uri.isAbsolute() && prefix.endsWith("/") && uri.getRawQuery() == null && uri.getRawFragment() == null);
            }
            for (int i = 0; i < allowedPeerUriPrefixes.size(); i++) {
                for (int j = i + 1; j < allowedPeerUriPrefixes.size(); j++) {
                    String first = allowedPeerUriPrefixes.get(i);
                    String second = allowedPeerUriPrefixes.get(j);
                    AssertUtils.argTrue("Bolt peer URI prefixes must not overlap", !first.startsWith(second) && !second.startsWith(first));
                }
            }
            if (mode == BoltTransportMode.MTLS) {
                AssertUtils.argTrue("Bolt MTLS requires TLS", tls != null && tls.isEnabled());
                AssertUtils.argNotBlank("Bolt TLS key-store is required", tls.getKeyStore());
                AssertUtils.argNotNull("Bolt TLS key-store password is required", tls.getKeyStorePassword());
            } else {
                AssertUtils.argTrue("Bolt development requires explicit synthetic data", development != null && development.isSyntheticData());
                AssertUtils.argTrue("Bolt development cannot enable TLS", tls != null && !tls.isEnabled());
            }
            AssertUtils.argTrue("Bolt creation timeout must be positive", connectionCreationTimeout != null
                    && !connectionCreationTimeout.isNegative() && !connectionCreationTimeout.isZero());
            AssertUtils.argTrue("Bolt resource limits must be positive and one connection per target",
                    creationThreads > 0 && creationQueueCapacity > 0 && maxTargetPools > 0 && maxConnectionsPerTarget == 1
                            && maxPendingCallers > 0 && maxInflightRpcs > 0);
            AssertUtils.argTrue("Bolt background creation is disabled", !backgroundReconnectEnabled && !connectionWarmupEnabled);
        }

        /**
         * 校验策略并选择目标所属的身份 URI 前缀，要求前缀后存在非空身份后缀；无匹配时拒绝。
         */
        public String selectPeerUriPrefix(BoltTarget target) {
            validateTypedPolicy();
            return allowedPeerUriPrefixes.stream()
                    .filter(prefix -> target.expectedPeerIdentity().startsWith(prefix) && target.expectedPeerIdentity().length() > prefix.length())
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Bolt target does not match a configured URI prefix"));
        }

        /**
         * 复制传输、安全与资源策略，隔离原配置后续修改；返回对象仍可变，不复制装配开关 enabled。
         * TLS 只复制材料路径及选项，不固定路径所指向的文件内容；调用前须保证嵌套配置和前缀列表有效。
         */
        public Client snapshot() {
            Client fixed = new Client();
            fixed.setMode(mode);
            Development fixedDevelopment = new Development();
            fixedDevelopment.setSyntheticData(development.isSyntheticData());
            fixedDevelopment.setIsolatedContainerNetwork(development.isIsolatedContainerNetwork());
            fixed.setDevelopment(fixedDevelopment);
            fixed.setAllowedPeerUriPrefixes(List.copyOf(allowedPeerUriPrefixes));
            fixed.setConnectionCreationTimeout(connectionCreationTimeout);
            fixed.setCreationThreads(creationThreads);
            fixed.setCreationQueueCapacity(creationQueueCapacity);
            fixed.setMaxTargetPools(maxTargetPools);
            fixed.setMaxConnectionsPerTarget(maxConnectionsPerTarget);
            fixed.setMaxPendingCallers(maxPendingCallers);
            fixed.setMaxInflightRpcs(maxInflightRpcs);
            fixed.setBackgroundReconnectEnabled(backgroundReconnectEnabled);
            fixed.setConnectionWarmupEnabled(connectionWarmupEnabled);
            Tls fixedTls = new Tls();
            fixedTls.setEnabled(tls.isEnabled());
            fixedTls.setNeedClientAuth(tls.isNeedClientAuth());
            fixedTls.setKeyStore(tls.getKeyStore());
            fixedTls.setKeyStorePassword(tls.getKeyStorePassword());
            fixedTls.setKeyStoreType(tls.getKeyStoreType());
            fixed.setTls(fixedTls);
            return fixed;
        }
    }

    /**
     * Bolt 服务端的监听地址、传输模式及安全材料配置。
     */
    @Data
    public static class Server {
        /* 是否装配默认受控服务端。 */
        private boolean enabled;

        /* 服务端监听地址；默认监听所有本机接口，部署时应显式限制暴露范围。 */
        private String host = "0.0.0.0";

        /* Bolt 服务端监听端口。 */
        private int port = 12200;
        /* 服务端传输模式；同时启用客户端时须与客户端模式一致。 */
        private BoltTransportMode mode = BoltTransportMode.MTLS;
        /* 服务端明文开发模式的显式安全声明。 */
        private Development development = new Development();
        /* 服务端 TLS 材料与双向认证选项。 */
        private Tls tls = new Tls();
    }

    /**
     * 受限明文开发模式的部署声明；声明本身不证明网络已经隔离。
     */
    @Data
    public static class Development {
        /* 显式声明仅使用合成数据，不允许承载生产数据。 */
        private boolean syntheticData;
        /* 声明使用受控隔离容器网络，允许的网络地址仍需由准入与部署流程验证。 */
        private boolean isolatedContainerNetwork;
    }

    /**
     * 原生 TLS 初始化使用的 keystore 材料与认证选项，不实现材料内容快照或自动轮换。
     */
    @Data
    public static class Tls {
        /* 是否启用原生 TLS；受控 MTLS 模式要求为 true。 */
        private boolean enabled;
        /* 服务端是否要求客户端证书；受控 MTLS 服务端要求为 true。 */
        private boolean needClientAuth = true;
        /* keystore 材料路径，供原生 TLS 流程加载本机私钥链和信任材料。 */
        private String keyStore;
        /* keystore 密码，不参与 Lombok 生成的 toString；部署时通过 secret 提供。 */
        @ToString.Exclude
        private String keyStorePassword;
        /* keystore 格式，默认 PKCS12。 */
        private String keyStoreType = "PKCS12";
    }
}
