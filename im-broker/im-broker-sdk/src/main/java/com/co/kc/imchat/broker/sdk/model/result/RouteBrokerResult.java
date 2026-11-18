package com.co.kc.imchat.broker.sdk.model.result;

import java.io.Serializable;

/**
 * RouteBroker 调用结果。非 EXECUTED 状态保证尚未产生路由副作用。
 *
 * @param status              执行或明确拒绝状态
 * @param routingEpoch        接收 Broker 的本地 epoch
 * @param suggestedEndpoint   建议刷新后访问的 RouteBroker 或 Leader 端点
 * @param value               已执行请求的业务结果
 */
public record RouteBrokerResult(RouteBrokerStatus status,
                                long routingEpoch,
                                String suggestedEndpoint,
                                Object value) implements Serializable {

    public RouteBrokerResult {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (routingEpoch < 0L) {
            throw new IllegalArgumentException("routingEpoch must not be negative");
        }
    }

    public static RouteBrokerResult executed(Object value) {
        return new RouteBrokerResult(RouteBrokerStatus.EXECUTED, 0L, null, value);
    }

    public static RouteBrokerResult executed(long routingEpoch, Object value) {
        return new RouteBrokerResult(RouteBrokerStatus.EXECUTED, routingEpoch, null, value);
    }

    public static RouteBrokerResult rejected(RouteBrokerStatus status,
                                             long routingEpoch,
                                             String suggestedEndpoint) {
        if (status == RouteBrokerStatus.EXECUTED) {
            throw new IllegalArgumentException("rejected result cannot use EXECUTED status");
        }
        return new RouteBrokerResult(status, routingEpoch, suggestedEndpoint, null);
    }
}
