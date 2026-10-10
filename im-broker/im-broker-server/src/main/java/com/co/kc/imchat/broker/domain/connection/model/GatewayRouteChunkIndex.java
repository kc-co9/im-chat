package com.co.kc.imchat.broker.domain.connection.model;

import com.co.kc.imchat.common.utils.AssertUtils;

/**
 * Gateway 路由批次内从零开始的分片序号，用于重复识别与按序组装。
 * 序号必须非负，是否超出某一批次的分片数量由批次实体判断。
 */
public record GatewayRouteChunkIndex(Integer value) {
    public GatewayRouteChunkIndex {
        AssertUtils.domainPropNotNull("Gateway route chunk index is required", value);
        AssertUtils.domainPropTrue("Gateway route chunk index must not be negative", value >= 0);
    }
}
