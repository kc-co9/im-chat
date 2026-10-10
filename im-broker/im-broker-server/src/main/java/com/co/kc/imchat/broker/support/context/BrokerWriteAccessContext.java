package com.co.kc.imchat.broker.support.context;



/**
 * 管理写请求的协议上下文，由 HTTP 拦截器从 Header 提取。
 */
public record BrokerWriteAccessContext(String operationId, Long expectedEpoch) {
}
