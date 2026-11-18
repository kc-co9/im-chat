package com.co.kc.imchat.broker.domain.cluster.repository;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerRegistration;


import java.util.List;
import java.util.Optional;

/**
 * Broker 实例索引。
 */
public interface BrokerRegistry {

    /**
     * 保存或刷新 Broker 实例。
     *
     * @param brokerId Broker 实例 ID
     * @param host Broker 实例地址
     * @param port Broker 实例端口
     */
    void register(String brokerId, String host, int port);

    /**
     * 删除 Broker 实例。
     *
     * @param brokerId Broker 实例 ID
     */
    void unregister(String brokerId);

    /**
     * 刷新 Broker 实例活跃时间。
     *
     * @param brokerId Broker 实例 ID
     */
    void heartbeat(String brokerId);

    /**
     * 查询 Broker 实例。
     *
     * @param brokerId Broker 实例 ID
     * @return Broker 实例
     */
    Optional<BrokerRegistration> find(String brokerId);

    /**
     * 查询全部 Broker 实例。
     *
     * @return Broker 实例列表
     */
    List<BrokerRegistration> list();
}
