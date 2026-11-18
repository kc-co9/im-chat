package com.co.kc.imchat.broker.support.context;


/** Broker 管理写接口接受的授权 scope。 */
public enum BrokerWriteAccessScope {
    /** 更改 Broker 路由可用性的权限。 */
    CLUSTER_STATE_WRITE("ops:broker:cluster-state:write"),
    /** 更改 Raft 成员关系的权限。 */
    RAFT_WRITE("ops:broker:raft:write"),
    /** 绕过正常 drain 完成条件的强制操作权限。 */
    FORCE("ops:broker:force");

    private final String value;

    BrokerWriteAccessScope(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
