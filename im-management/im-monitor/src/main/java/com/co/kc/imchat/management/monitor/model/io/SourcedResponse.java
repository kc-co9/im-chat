package com.co.kc.imchat.management.monitor.model.io;

/** 携带来源 Broker 的响应项。 */
public record SourcedResponse<T>(String sourceBroker, T value) {
}
