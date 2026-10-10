package com.co.kc.imchat.broker.domain.authority.repository;

import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;

import java.util.Optional;

/**
 * 本机路由 fence 水位的领域资源库。
 *
 * <p>资源库保存的是“本 Broker 已经 fence 到哪个路由纪元”的领域事实，而不是普通缓存。
 * 实现必须保证最高已 fence epoch 单调持久化，使 Broker 重启后仍不能恢复旧 epoch 的执行权。</p>
 */
public interface FenceWatermarkRepository {

    Optional<RoutingEpoch> highestFencedEpoch();

    void advanceTo(RoutingEpoch routingEpoch);
}
