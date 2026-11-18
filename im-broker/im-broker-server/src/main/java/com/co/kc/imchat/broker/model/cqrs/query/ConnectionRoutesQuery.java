package com.co.kc.imchat.broker.model.cqrs.query;

/** 查询指定用户在当前 Broker 保存的连接路由观察。 */
public record ConnectionRoutesQuery(/* 连接归属用户。 */ Long userId) {
}
