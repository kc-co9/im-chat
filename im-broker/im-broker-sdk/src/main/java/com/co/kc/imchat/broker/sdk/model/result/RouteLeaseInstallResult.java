package com.co.kc.imchat.broker.sdk.model.result;

import java.io.Serializable;

/**
 * 目标 Broker 是否已接受并安装本次下发的 Lease。
 */
public record RouteLeaseInstallResult(boolean accepted) implements Serializable {
}
