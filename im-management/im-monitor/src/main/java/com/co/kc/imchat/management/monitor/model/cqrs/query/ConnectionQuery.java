package com.co.kc.imchat.management.monitor.model.cqrs.query;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 按用户查询连接路由。 */
public record ConnectionQuery(Long userId) {
    public ConnectionQuery {
        AssertUtils.argTrue("userId must be positive", userId != null && userId > 0);
    }
}
