package com.co.kc.imchat.broker.sdk.model.params;

import com.co.kc.imchat.broker.sdk.access.BrokerPeerRequest;
import java.io.Serializable;

/**
 * Broker 实例心跳请求。
 *
 * @param brokerId Broker 实例 ID
 */
public record BrokerHeartbeatParams(String brokerId)
        implements Serializable, BrokerPeerRequest {
}
