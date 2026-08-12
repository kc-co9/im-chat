package com.co.kc.imchat.plugin.mq.model;

public record MqMessage<T>(String topic, T payload) {
}
