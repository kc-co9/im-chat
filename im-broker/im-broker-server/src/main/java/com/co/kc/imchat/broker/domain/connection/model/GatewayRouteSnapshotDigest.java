package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.common.utils.AssertUtils;


/**
 * 绑定完整连接快照内容的 SHA-256 摘要值对象。
 *
 * <p>GatewayRouteTable 将用户集合去重、排序后计算摘要，用它识别同一 revision 的幂等重试，
 * 并拒绝相同 revision 但内容不同的快照。摘要只用于内容绑定，不用于恢复用户列表。</p>
 */
public record GatewayRouteSnapshotDigest(String value) {

    public GatewayRouteSnapshotDigest {
        AssertUtils.domainPropNotBlank("snapshot content digest must not be blank", value);
        AssertUtils.domainPropTrue("snapshot content digest must be lowercase SHA-256",
                value.matches("[0-9a-f]{64}"));
    }

}
