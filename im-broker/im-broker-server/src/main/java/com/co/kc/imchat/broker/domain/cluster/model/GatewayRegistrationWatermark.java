package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * drain 开始时观察到的 Gateway 注册水位值对象。
 *
 * <p>该水位保存冻结时 Broker 注册表观察到的 Gateway lastSeenAt 毫秒值，不表示
 * Gateway 已确认的注册进度，也不是连接路由 revision 或连接数量。</p>
 */
public record GatewayRegistrationWatermark(Long value) implements Serializable {

    public GatewayRegistrationWatermark {
        AssertUtils.domainPropNotNull("gateway registration watermark is required", value);
        AssertUtils.domainPropTrue("gateway registration watermark must not be negative",
                value >= 0L);
    }
}
