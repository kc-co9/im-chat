package com.co.kc.imchat.management.monitor.model.cqrs.query;

import com.co.kc.imchat.common.utils.AssertUtils;

/** 查询指定 Broker。 */
public record BrokerGetQuery(String brokerId) {
    public BrokerGetQuery {
        AssertUtils.argNotBlank("brokerId must not be blank", brokerId);
    }
}
