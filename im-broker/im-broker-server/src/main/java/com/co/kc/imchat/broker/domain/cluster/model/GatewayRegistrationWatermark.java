package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * drain 开始时观察到的 Gateway 注册水位值对象。
 *
 * <p>该水位用于标记冻结时已经被 Gateway 接受的注册进度，帮助恢复流程区分冻结前后的
 * 路由事件。它是单调计数事实，不是连接路由 revision，也不直接表示当前在线连接数。</p>
 */
public record GatewayRegistrationWatermark(Long value) implements Serializable {

    public GatewayRegistrationWatermark {
        AssertUtils.domainPropNotNull("gateway registration watermark is required", value);
        AssertUtils.domainPropTrue("gateway registration watermark must not be negative",
                value >= 0L);
    }
}
