package com.co.kc.imchat.broker.model.cqrs.result;

/** Broker 突然故障后的路由重建协调结果。 */
public record BrokerFailureRecoveryResult(Status status, String message) {
    /** 返回 SUCCEEDED 阶段的协调结果。 */
    public static BrokerFailureRecoveryResult succeeded() {
        return new BrokerFailureRecoveryResult(Status.SUCCEEDED, null);
    }

    /** 返回 DEGRADED 阶段的协调结果。 */
    public static BrokerFailureRecoveryResult degraded(String message) {
        return new BrokerFailureRecoveryResult(Status.DEGRADED, message);
    }

    /** 返回 WAITING_FOR_GATEWAYS 阶段的协调结果。 */
    public static BrokerFailureRecoveryResult waitingForGateways(String message) {
        return new BrokerFailureRecoveryResult(Status.WAITING_FOR_GATEWAYS, message);
    }

    /** 返回 WAITING_FOR_OLD_EVENTS 阶段的协调结果。 */
    public static BrokerFailureRecoveryResult waitingForOldEvents(String message) {
        return new BrokerFailureRecoveryResult(Status.WAITING_FOR_OLD_EVENTS, message);
    }

    public enum Status {
        /** 等待 Gateway 提供恢复快照。 */
        WAITING_FOR_GATEWAYS,
        /** 等待旧 epoch 事件失效。 */
        WAITING_FOR_OLD_EVENTS,
        /** 故障摘流后的路由已恢复。 */
        SUCCEEDED,
        /** 服务可继续但恢复证据不完整。 */
        DEGRADED
    }
}
