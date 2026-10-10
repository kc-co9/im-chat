package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * GatewayRouteTable 最近已接受的完整连接快照检查点值对象。
 *
 * <p>只保存快照 revision 和规范化用户集合的内容摘要，不保存用户列表。它用于忽略旧
 * 快照、将同 revision 同内容识别为幂等重试，并拒绝同 revision 不同内容的协议冲突，
 * 从而使完整快照可以安全重传。</p>
 */
public record GatewayRouteSnapshotCheckpoint(RouteRevision revision,
                                             GatewayRouteSnapshotDigest snapshotDigest) {

    public GatewayRouteSnapshotCheckpoint {
        AssertUtils.allDomainPropNotNull("gateway route snapshot checkpoint is incomplete",
                revision, snapshotDigest);
    }
}
