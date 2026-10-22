package com.co.kc.imchat.broker.sdk.model.result;

import com.co.kc.imchat.common.utils.AssertUtils;
import java.io.Serializable;
import java.util.List;

/** 回显本轮上下文及响应者自身身份。 */
public record BrokerHealthReportResult(
        /* 集群绑定身份。 */
        String clusterId,
        /* 报告者自身身份。 */
        String observerBrokerId,
        /* 提交版本。 */
        Long revision,
        /* 路由代次。 */
        Long epoch,
        /* 本轮防重放关联值。 */
        String nonce,
        /* 不可变完整报告集合。 */
        List<BrokerHealthReportRow> observations) implements Serializable {
    public BrokerHealthReportResult {
        AssertUtils.argNotBlank("clusterId is required", clusterId);
        AssertUtils.argNotBlank("observerBrokerId is required", observerBrokerId);
        AssertUtils.argTrue("revision must be nonnegative", revision != null && revision >= 0L);
        AssertUtils.argTrue("epoch must be nonnegative", epoch != null && epoch >= 0L);
        AssertUtils.argNotBlank("nonce is required", nonce);
        AssertUtils.argNotNull("observations is required", observations);
        observations = List.copyOf(observations);
        AssertUtils.argTrue("duplicate report target", observations.stream().map(BrokerHealthReportRow::brokerId).distinct().count() == observations.size());
    }
}
