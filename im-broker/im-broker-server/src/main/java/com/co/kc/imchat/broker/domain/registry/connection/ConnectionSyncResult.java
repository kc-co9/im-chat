package com.co.kc.imchat.broker.domain.registry.connection;

import com.co.kc.imchat.broker.sdk.model.dto.UserGatewayDTO;

import java.util.List;

/**
 * 网关连接快照对账结果。
 */
public record ConnectionSyncResult(List<UserGatewayDTO> added,
                                   List<UserGatewayDTO> removed) {
}
