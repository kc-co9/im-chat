package com.co.kc.imchat.broker.domain.authority.model;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * Broker 针对当前 committed epoch 申请路由执行许可的领域请求。
 *
 * <p>请求绑定申请者、目标路由 epoch 和一次性 nonce。权威签发方用这些事实校验
 * 请求是否仍对应当前提交状态，并把 nonce 贯穿 prepare、grant 和 install，避免旧请求
 * 或重复请求获得执行权。</p>
 */
public record LeaseGrantRequest(
        /* 申请 Lease 的 Broker。 */
        BrokerId brokerId,
        /* 申请执行权的 committed epoch。 */
        RoutingEpoch routingEpoch,
        /* 绑定 prepare 与后续 install 的一次性随机值。 */
        LeaseNonce leaseNonce) {

    public LeaseGrantRequest {
        AssertUtils.allDomainPropNotNull("lease grant request is incomplete",
                brokerId, routingEpoch, leaseNonce);
    }
}
