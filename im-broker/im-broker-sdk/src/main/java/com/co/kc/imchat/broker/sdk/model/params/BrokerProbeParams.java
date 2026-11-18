package com.co.kc.imchat.broker.sdk.model.params;

import com.co.kc.imchat.broker.sdk.access.BrokerObservationRequest;
import com.co.kc.imchat.common.utils.AssertUtils;
import java.io.Serializable;

/** 单轮 Broker 健康观察请求。 */
public record BrokerProbeParams(
        /* 集群绑定身份。 */
        String clusterId,
        /* 调用方声明；必须与认证来源核对。 */
        String callerBrokerId,
        /* 请求接收方身份。 */
        String receiverBrokerId,
        /* 提交版本。 */
        Long revision,
        /* 路由代次。 */
        Long epoch,
        /* 本轮防重放关联值。 */
        String nonce) implements Serializable, BrokerObservationRequest {
    public BrokerProbeParams {
        AssertUtils.argNotBlank("clusterId is required", clusterId);
        AssertUtils.argNotBlank("callerBrokerId is required", callerBrokerId);
        AssertUtils.argNotBlank("receiverBrokerId is required", receiverBrokerId);
        AssertUtils.argTrue("revision must be nonnegative", revision != null && revision >= 0L);
        AssertUtils.argTrue("epoch must be nonnegative", epoch != null && epoch >= 0L);
        AssertUtils.argNotBlank("nonce is required", nonce);
    }
}
