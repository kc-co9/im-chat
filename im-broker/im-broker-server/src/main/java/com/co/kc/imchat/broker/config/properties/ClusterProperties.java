package com.co.kc.imchat.broker.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Broker 集群 Gossip 同步配置。
 * <p>
 * 用于配置集群初始节点发现、每轮 Gossip 的同步范围、请求超时时间以及删除标记的保留时间。
 */
@Data
@ConfigurationProperties(prefix = "im.broker.cluster")
public class ClusterProperties {

    /**
     * Gossip 初始种子节点地址，地址格式为 {@code host:port}。
     * <p>
     * Broker 启动时通过种子节点加入集群，后续也会从 Gossip 状态中发现其他 Broker。
     */
    private List<String> seedAddresses = new ArrayList<>();

    /**
     * 每轮 Gossip 随机选择并同步的 Broker 节点数量。
     * <p>
     * 数量越大，状态扩散越快，同时产生的网络请求也越多；有效值最小为 {@code 1}。
     */
    private int gossipFanout = 2;

    /**
     * 单次 Gossip 同步请求的超时时间，单位为毫秒。
     */
    private int gossipTimeoutMillis = 3000;

    /**
     * Gossip 删除标记的保留时间，单位为毫秒。
     * <p>
     * 删除标记需要保留一段时间，以便将删除状态传播到其他 Broker；超时后可从本地状态中清理。
     * 该值应大于 Broker 允许离线后重新加入集群的最长时间，避免旧状态重新传播。
     */
    private long gossipRemovedTtlMillis = 300000;

    /**
     * 获取每轮 Gossip 使用的有效扇出数量。
     *
     * @return 不小于 {@code 1} 的节点数量
     */
    public int getGossipFanout() {
        return Math.max(1, gossipFanout);
    }
}
