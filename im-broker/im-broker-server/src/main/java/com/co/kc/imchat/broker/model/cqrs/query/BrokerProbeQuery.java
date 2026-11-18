package com.co.kc.imchat.broker.model.cqrs.query;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * Broker 健康观察应用查询；边界输入在创建时完整校验。
 */
public record BrokerProbeQuery(
        /* 集群身份。 */
        String clusterId,
        /* 已认证调用方。 */
        String callerBrokerId,
        /* 请求接收方身份。 */
        String receiverBrokerId,
        /* 提交版本。 */
        Long revision,
        /* 路由代次。 */
        Long epoch,
        /* 本轮关联身份。 */
        String nonce) {
    public BrokerProbeQuery {
        AssertUtils.argNotBlank("clusterId is required", clusterId);
        AssertUtils.argNotBlank("callerBrokerId is required", callerBrokerId);
        AssertUtils.argNotBlank("receiverBrokerId is required", receiverBrokerId);
        AssertUtils.argNotBlank("nonce is required", nonce);
        AssertUtils.argTrue("revision must be nonnegative", revision != null && revision >= 0L);
        AssertUtils.argTrue("epoch must be nonnegative", epoch != null && epoch >= 0L);
    }
}
