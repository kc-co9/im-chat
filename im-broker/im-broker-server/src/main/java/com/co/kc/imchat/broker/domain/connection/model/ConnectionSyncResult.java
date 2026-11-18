package com.co.kc.imchat.broker.domain.connection.model;

import java.util.List;

/**
 * 网关连接快照对账结果。
 */
public record ConnectionSyncResult(List<ConnectionRegistration> added,
                                   List<ConnectionRegistration> removed) {
}
