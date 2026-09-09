package com.co.kc.imchat.management.monitor.domain.authorization.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Monitor 拥有的有界只读诊断权限。 */
@Getter
@RequiredArgsConstructor
public enum MonitorPermission {
    OVERVIEW_READ(Code.OVERVIEW_READ, "查看集群概览", "查看 Broker 集群概览"),
    BROKER_READ(Code.BROKER_READ, "查看 Broker", "查看 Broker 节点与详情"),
    GATEWAY_READ(Code.GATEWAY_READ, "查看 Gateway", "查看 Gateway 节点"),
    CONNECTION_READ(Code.CONNECTION_READ, "查看连接路由", "按用户查看有界连接路由"),
    DIAGNOSTIC_READ(Code.DIAGNOSTIC_READ, "查看诊断状态", "查看 Gossip 与迁移诊断记录");

    private final String code;
    private final String displayName;
    private final String description;

    /** Monitor 权限编码。 */
    public static final class Code {
        public static final String OVERVIEW_READ = "monitor:overview:read";
        public static final String BROKER_READ = "monitor:broker:read";
        public static final String GATEWAY_READ = "monitor:gateway:read";
        public static final String CONNECTION_READ = "monitor:connection:read";
        public static final String DIAGNOSTIC_READ = "monitor:diagnostic:read";

        private Code() {
        }
    }
}
