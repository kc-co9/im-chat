package com.co.kc.imchat.broker.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Broker 实例与客户端调用配置。
 */
@Data
@ConfigurationProperties(prefix = "im.broker")
public class BrokerProperties {

    /**
     * 当前 Broker 实例配置。
     */
    private Instance instance = new Instance();

    /**
     * Broker 向 Gateway 推送数据的客户端配置。
     */
    private GatewayPush gatewayPush = new GatewayPush();

    /**
     * Broker 节点间调用配置。
     */
    private PeerCall peerCall = new PeerCall();

    /**
     * 当前 Broker 实例的网络端点配置。
     */
    @Data
    public static class Instance {

        /**
         * Broker Bolt 服务监听主机。
         */
        private String host = "127.0.0.1";

        /**
         * Broker Bolt 服务监听端口。
         */
        private int port = 12200;

        /**
         * 根据网络端点生成当前 Broker 实例 ID。
         *
         * @return Broker 实例 ID
         */
        public String getId() {
            return "broker-" + host + "-" + port;
        }

        /**
         * 获取当前 Broker 的 Bolt 服务地址。
         *
         * @return host:port 格式的服务地址
         */
        public String getAddress() {
            return host + ":" + port;
        }
    }

    /**
     * Broker 向 Gateway 推送数据的配置。
     */
    @Data
    public static class GatewayPush {

        /**
         * Gateway Bolt 客户端配置。
         */
        private Bolt bolt = new Bolt();

        /**
         * Gateway Bolt RPC 调用配置。
         */
        @Data
        public static class Bolt {

            /**
             * 单次 Gateway Bolt RPC 调用超时时间，单位为毫秒。
             */
            private int timeoutMs = 3000;
        }
    }

    /**
     * Broker 节点间 RPC 调用配置。
     */
    @Data
    public static class PeerCall {

        /* 控制面 peer RPC 的最大并发线程数。 */
        private int maxConcurrentRequests = 8;

        /* 等待执行的控制面 peer RPC 最大数量，超过上限直接拒绝，不回退到调用线程。 */
        private int maxPendingRequests = 128;

        /**
         * 单次 Broker 节点间 RPC 调用超时时间，单位为毫秒。
         */
        private int timeoutMs = 3000;
    }
}
