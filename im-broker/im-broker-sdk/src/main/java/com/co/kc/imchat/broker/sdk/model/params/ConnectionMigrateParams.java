package com.co.kc.imchat.broker.sdk.model.params;

import com.co.kc.imchat.broker.sdk.model.dto.ConnectionMigrationDTO;

import java.util.List;

/**
 * 用户连接迁移请求。
 *
 * @param connections 迁移到目标 owner Broker 的连接
 */
public record ConnectionMigrateParams(List<ConnectionMigrationDTO> connections) {
    public ConnectionMigrateParams {
        connections = connections == null ? List.of() : List.copyOf(connections);
    }
}
