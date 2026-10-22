package com.co.kc.imchat.broker.sdk.model.params;

import com.co.kc.imchat.broker.sdk.access.BrokerDataPlaneRequest;

import java.io.Serializable;

/** Broker peer 只读路由查询；hopCount 必须为 1。 */
public record RouteLookupParams(String requesterBrokerId,
                                long routingEpoch,
                                Long targetUserId,
                                long expireAtMs,
                                int hopCount) implements Serializable, BrokerDataPlaneRequest {
}
