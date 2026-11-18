package com.co.kc.imchat.management.ops.domain.authorization.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Ops 拥有的诊断与受控 Broker 操作权限。 */
@Getter
@RequiredArgsConstructor
public enum OpsPermission {
    OVERVIEW_READ(Code.OVERVIEW_READ, "查看集群概览", "查看 Broker 集群概览"),
    BROKER_READ(Code.BROKER_READ, "查看 Broker", "查看 Broker 节点与详情"),
    GATEWAY_READ(Code.GATEWAY_READ, "查看 Gateway", "查看 Gateway 节点"),
    CONNECTION_READ(Code.CONNECTION_READ, "查看连接路由", "按用户查看有界连接路由"),
    DIAGNOSTIC_READ(Code.DIAGNOSTIC_READ, "查看诊断状态", "查看 Gossip 与迁移诊断记录"),
    BROKER_CLUSTER_STATE_WRITE(Code.BROKER_CLUSTER_STATE_WRITE,
            "变更 Broker 分流状态", "激活、排空或停止 Broker 分流"),
    BROKER_RAFT_WRITE(Code.BROKER_RAFT_WRITE,
            "变更 Broker Raft 成员", "加入、晋升、降级或移除 Raft 成员"),
    BROKER_FORCE(Code.BROKER_FORCE,
            "强制结束 Broker 操作", "接受剩余风险并强制结束失败操作");

    private final String code;
    private final String displayName;
    private final String description;

    /** Ops 权限编码。 */
    public static final class Code {
        public static final String OVERVIEW_READ = "ops:overview:read";
        public static final String BROKER_READ = "ops:broker:read";
        public static final String GATEWAY_READ = "ops:gateway:read";
        public static final String CONNECTION_READ = "ops:connection:read";
        public static final String DIAGNOSTIC_READ = "ops:diagnostic:read";
        public static final String BROKER_CLUSTER_STATE_WRITE =
                "ops:broker:cluster-state:write";
        public static final String BROKER_RAFT_WRITE = "ops:broker:raft:write";
        public static final String BROKER_FORCE = "ops:broker:force";

        private Code() {
        }
    }
}
