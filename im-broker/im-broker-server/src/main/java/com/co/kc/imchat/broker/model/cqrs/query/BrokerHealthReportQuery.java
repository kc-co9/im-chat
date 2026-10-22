package com.co.kc.imchat.broker.model.cqrs.query;

import com.co.kc.imchat.common.utils.AssertUtils;
import java.util.HashSet;
import java.util.List;

/**
 * Broker 健康观察应用查询；边界输入在创建时完整校验。
 */
public record BrokerHealthReportQuery(
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
        String nonce,
        /* 本轮目标集合。 */
        List<String> brokerIds) {
    public BrokerHealthReportQuery {
        AssertUtils.argNotBlank("clusterId is required", clusterId);
        AssertUtils.argNotBlank("callerBrokerId is required", callerBrokerId);
        AssertUtils.argNotBlank("receiverBrokerId is required", receiverBrokerId);
        AssertUtils.argNotBlank("nonce is required", nonce);
        AssertUtils.argTrue("revision must be nonnegative", revision != null && revision >= 0L);
        AssertUtils.argTrue("epoch must be nonnegative", epoch != null && epoch >= 0L);
        AssertUtils.argNotNull("brokerIds is required", brokerIds);
        for (String brokerId : brokerIds) {
            AssertUtils.argNotBlank("report target is required", brokerId);
        }
        AssertUtils.argTrue("duplicate report target", new HashSet<>(brokerIds).size() == brokerIds.size());
        brokerIds = List.copyOf(brokerIds);
    }
}
