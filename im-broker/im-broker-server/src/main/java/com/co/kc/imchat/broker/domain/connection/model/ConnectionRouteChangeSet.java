package com.co.kc.imchat.broker.domain.connection.model;

import java.util.List;

/**
 * GatewayRouteTable 领域行为产生的连接投影变更结果。
 *
 * <p>该结果把聚合内部状态变化转换为外部 ConnectionRegistry 需要执行的新增和删除动作。
 * accepted 表示命令是否被聚合处理，changed 只表示外部投影是否真的需要变化，两者不能
 * 混为一谈：重复或过期命令可能被拒绝且没有变更，合法更新也可能只刷新内部 revision。</p>
 *
 * @param accepted 是否接受并处理本次命令；即使连接投影没有增删也可能为 {@code true}
 * @param added 本次需要加入外部连接投影的路由
 * @param removed 本次需要移出外部连接投影的路由
 */
public record ConnectionRouteChangeSet(Boolean accepted,
                                       List<ConnectionRouteId> added,
                                       List<ConnectionRouteId> removed) {

    public ConnectionRouteChangeSet {
        if (accepted == null) {
            accepted = false;
        }
        added = added == null ? List.of() : List.copyOf(added);
        removed = removed == null ? List.of() : List.copyOf(removed);
    }

    public static ConnectionRouteChangeSet unchanged() {
        return new ConnectionRouteChangeSet(false, List.of(), List.of());
    }

    /**
     * 返回本次命令是否改变了外部连接投影，不表示聚合是否接受该命令。
     */
    public boolean changed() {
        return !added.isEmpty() || !removed.isEmpty();
    }
}
