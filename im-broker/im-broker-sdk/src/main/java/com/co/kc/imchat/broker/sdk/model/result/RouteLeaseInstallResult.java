package com.co.kc.imchat.broker.sdk.model.result;

import java.io.Serializable;

/**
 * holder 是否已经接受并安装对应 Lease。
 */
public record RouteLeaseInstallResult(boolean accepted) implements Serializable {
}
