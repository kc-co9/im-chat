package com.co.kc.imchat.broker.sdk.access;



/** 绑定调用方、目标及单轮上下文的观察协议；不携带路由 Lease。 */
public interface BrokerObservationRequest {
    String clusterId();
    String callerBrokerId();
    String receiverBrokerId();
    Long revision();
    Long epoch();
    String nonce();
}
