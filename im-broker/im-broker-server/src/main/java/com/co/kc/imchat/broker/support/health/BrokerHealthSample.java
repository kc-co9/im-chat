package com.co.kc.imchat.broker.support.health;

import com.co.kc.imchat.broker.domain.authority.model.MonotonicTimestamp;
import com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthStatus;

/** 本机一次探测的状态与单调采样时刻，不包含报告者身份或故障处置决策。 */
public record BrokerHealthSample(BrokerHealthStatus status, MonotonicTimestamp completedAt) {
}
