package com.co.kc.imchat.broker.sdk.model.result;

import com.co.kc.imchat.common.utils.AssertUtils;
import java.io.Serializable;

/** 回显本轮上下文及响应者自身身份。 */
public record BrokerProbeResult(
        /* 集群绑定身份。 */
        String clusterId,
        /* 响应者身份。 */
        String brokerId,
        /* 提交版本。 */
        Long revision,
        /* 路由代次。 */
        Long epoch,
        /* 本轮防重放关联值。 */
        String nonce) implements Serializable {
    public BrokerProbeResult {
        AssertUtils.argNotBlank("clusterId is required", clusterId);
        AssertUtils.argNotBlank("brokerId is required", brokerId);
        AssertUtils.argTrue("revision must be nonnegative", revision != null && revision >= 0L);
        AssertUtils.argTrue("epoch must be nonnegative", epoch != null && epoch >= 0L);
        AssertUtils.argNotBlank("nonce is required", nonce);
    }
}
