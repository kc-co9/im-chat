package com.co.kc.imchat.gateway.ws.config.properties;

import com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * WS 网关配置项。
 * <p>
 * 集中管理 Netty 监听、连接空闲检测、Broker 通信和网关注册相关配置，避免配置键散落在 Bean 定义中。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = GatewayProperties.PREFIX)
public class GatewayProperties {
    public static final String PREFIX = "im.gateway.ws";

    /**
     * Netty WebSocket 监听端口。
     */
    private int port = 19090;

    /**
     * WebSocket 握手路径。
     */
    private String path = "/ws";

    /**
     * 当前网关对 Broker 暴露的地址主机名。
     */
    private String host = "127.0.0.1";

    /**
     * 单个 WebSocket 帧最大载荷大小。
     */
    private int maxFramePayloadLength = 65536;

    /**
     * 空闲连接检测配置。
     */
    private Idle idle = new Idle();

    /**
     * 当前网关 Bolt 服务注册信息。
     */
    private Bolt bolt = new Bolt();

    /**
     * Broker 访问配置。
     */
    private Broker broker = new Broker();

    /**
     * 网关注册刷新配置。
     */
    private Register register = new Register();

    public String gatewayId(int boltPort) {
        return "gateway-" + bolt.getHost() + "-" + boltPort;
    }

    @Data
    public static class Idle {
        /**
         * 读空闲超时时间，单位秒。
         */
        private int readerIdleSeconds = 60;
    }

    @Data
    public static class Bolt {
        /**
         * 当前网关对 Broker 暴露的 Bolt 主机名。
         */
        private String host = "127.0.0.1";
    }

    @Data
    public static class Broker {
        /**
         * Broker Bolt 调用配置。
         */
        private BrokerBolt bolt = new BrokerBolt();
    }

    @Data
    public static class BrokerBolt {
        /**
         * Broker Bolt 服务地址，多个地址用英文逗号分隔。
         */
        private String address = "127.0.0.1:12200";

        /**
         * Broker 地址负载均衡策略。
         */
        private BrokerLoadBalance loadBalance = BrokerLoadBalance.HASH;

        /**
         * 调用 Broker Bolt 服务的超时时间，单位毫秒。
         */
        private int timeoutMillis = 3000;
    }

    @Data
    public static class Register {
        /**
         * 是否启用网关向 Broker 的注册刷新任务。
         */
        private boolean enabled = true;
    }
}
