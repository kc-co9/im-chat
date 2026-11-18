package com.co.kc.imchat.broker.model.cqrs.result;

/** 一次 drain 协调轮次的可观测结果。 */
public record BrokerDrainResult(Status status, String message) {
    /** 返回 SUCCEEDED 阶段的协调结果。 */
    public static BrokerDrainResult succeeded() {
        return new BrokerDrainResult(Status.SUCCEEDED, null);
    }

    /** 返回 FAILED 阶段的协调结果。 */
    public static BrokerDrainResult failed(String message) {
        return new BrokerDrainResult(Status.FAILED, message);
    }

    /** 返回 FORCED 阶段的协调结果。 */
    public static BrokerDrainResult forced(String message) {
        return new BrokerDrainResult(Status.FORCED, message);
    }

    /** 返回 WAITING_FOR_GATEWAYS 阶段的协调结果。 */
    public static BrokerDrainResult waitingForGateways(String message) {
        return new BrokerDrainResult(Status.WAITING_FOR_GATEWAYS, message);
    }

    /** 返回 WAITING_FOR_CLEANUP 阶段的协调结果。 */
    public static BrokerDrainResult waitingForCleanup(String message) {
        return new BrokerDrainResult(Status.WAITING_FOR_CLEANUP, message);
    }

    /** 返回 WAITING_FOR_OLD_EVENTS 阶段的协调结果。 */
    public static BrokerDrainResult waitingForOldEvents(String message) {
        return new BrokerDrainResult(Status.WAITING_FOR_OLD_EVENTS, message);
    }

    public enum Status {
        /** 等待冻结集合中的 Gateway 确认完整快照。 */
        WAITING_FOR_GATEWAYS,
        /** 等待旧 RouteBroker 完成路由清理。 */
        WAITING_FOR_CLEANUP,
        /** 等待旧 epoch 事件全部失效。 */
        WAITING_FOR_OLD_EVENTS,
        /** drain 的快照、清理与验证均完成。 */
        SUCCEEDED,
        /** drain 超时或证据不满足条件。 */
        FAILED,
        /** 管理员明确接受残留风险后强制结束。 */
        FORCED
    }
}
