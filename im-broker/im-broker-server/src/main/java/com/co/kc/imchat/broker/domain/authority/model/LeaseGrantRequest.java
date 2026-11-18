package com.co.kc.imchat.broker.domain.authority.model;

import com.co.kc.imchat.broker.domain.cluster.model.BrokerId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * Broker 针对当前 committed epoch 申请路由执行许可的领域请求。
 *
 * <p>请求绑定申请者、目标路由 epoch 和一次性 nonce。权威签发方校验请求是否匹配当前提交
 * 状态；nonce 关联 prepare、grant 和 install，目标通过消费匹配的准备记录限制安装尝试，
 * 避免迟到或重复响应重新安装。</p>
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
