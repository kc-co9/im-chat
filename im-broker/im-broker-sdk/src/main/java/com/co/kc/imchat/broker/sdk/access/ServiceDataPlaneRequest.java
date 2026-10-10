package com.co.kc.imchat.broker.sdk.access;

/** 根据请求语义进一步约束服务数据面调用方角色。 */
public interface ServiceDataPlaneRequest {

    BrokerCallerRole requiredCallerRole();
}
