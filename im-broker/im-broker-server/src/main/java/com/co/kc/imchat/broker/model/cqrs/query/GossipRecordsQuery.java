package com.co.kc.imchat.broker.model.cqrs.query;

/** 查询最近的 Gossip 同步诊断记录，条数上限由技术查询能力校验。 */
public record GossipRecordsQuery(/* 请求返回的记录数量。 */ Integer limit) {
}
