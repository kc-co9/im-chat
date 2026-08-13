package com.co.kc.imchat.broker.sdk.model.result;

import java.io.Serializable;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerEndpointDTO;

import java.util.List;

/**
 * Broker 实例列表结果。
 *
 * @param brokers Broker 实例列表
 */
public record BrokerListResult(List<BrokerEndpointDTO> brokers) implements Serializable {
    public BrokerListResult {
        brokers = brokers == null ? List.of() : List.copyOf(brokers);
    }
}
