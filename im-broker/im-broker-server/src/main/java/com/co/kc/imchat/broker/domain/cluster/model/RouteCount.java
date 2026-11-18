package com.co.kc.imchat.broker.domain.cluster.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serializable;

/**
 * 连接路由条目的非负数量值对象。
 *
 * <p>该类型用于 Gateway 快照确认、RouteBroker 重建统计和 drain 完成判断。使用值对象
 * 是为了避免把“用户路由数量”与其他计数混用，并在累加时通过溢出检查保持统计结果可靠。</p>
 */
public record RouteCount(Long value) implements Serializable, Comparable<RouteCount> {

    public static final RouteCount ZERO = new RouteCount(0L);

    public RouteCount {
        AssertUtils.domainPropNotNull("route count is required", value);
        AssertUtils.domainPropTrue("route count must not be negative", value >= 0L);
    }

    public RouteCount plus(RouteCount other) {
        AssertUtils.domainPropNotNull("added route count is required", other);
        return new RouteCount(Math.addExact(value, other.value));
    }

    @Override
    public int compareTo(RouteCount other) {
        AssertUtils.domainPropNotNull("compared route count is required", other);
        return Long.compare(value, other.value);
    }
}
