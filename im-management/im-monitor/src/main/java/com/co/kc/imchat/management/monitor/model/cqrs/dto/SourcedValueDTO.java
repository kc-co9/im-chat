package com.co.kc.imchat.management.monitor.model.cqrs.dto;

/** 标明来源 Broker 的聚合数据项。 */
public record SourcedValueDTO<T>(String sourceBroker, T value) {
}
