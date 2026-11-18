package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.common.utils.AssertUtils;
import org.jspecify.annotations.NonNull;

/**
 * 同一 Gateway 实例内连接路由更新顺序的值对象。
 *
 * <p>注册、注销和完整快照都携带该 revision。GatewayRouteTable 只接受不早于当前状态的
 * 更新，并使用它把迟到事件、重复消息和新快照覆盖范围区分开；它只在单个 Gateway
 * 聚合内有意义，不等同于集群 revision 或路由 epoch。</p>
 */
public record RouteRevision(Long value) implements Comparable<RouteRevision> {

    public RouteRevision {
        AssertUtils.domainPropNotNull("route revision is required", value);
        AssertUtils.domainPropTrue("route revision must not be negative", value >= 0L);
    }

    /**
     * 返回两个 revision 中较大的一个。
     */
    public RouteRevision max(RouteRevision other) {
        AssertUtils.domainPropNotNull("compared route revision is required", other);
        return compareTo(other) >= 0 ? this : other;
    }

    @Override
    public int compareTo(@NonNull RouteRevision other) {
        AssertUtils.domainPropNotNull("compared route revision is required", other);
        return Long.compare(value, other.value);
    }
}
