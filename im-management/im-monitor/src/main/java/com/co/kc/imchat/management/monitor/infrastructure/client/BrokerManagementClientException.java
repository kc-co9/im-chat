package com.co.kc.imchat.management.monitor.infrastructure.client;

/** Broker 节点管理接口调用失败。 */
public class BrokerManagementClientException extends RuntimeException {
    public BrokerManagementClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
