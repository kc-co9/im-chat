package com.co.kc.imchat.management.ops.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * CLI 精确 Client 白名单和允许的 scope；未配置时默认拒绝 CLI 访问。
 */
@ConfigurationProperties(prefix = "im.ops.cli")
public record OpsCliProperties(
        /* 部署显式允许的 public Client ID。 */
        Set<String> clientIds,
        /* 本入口允许的 OAuth scope。 */
        Set<String> scopes
) {
    public OpsCliProperties {
        clientIds = clientIds == null ? Set.of() : Set.copyOf(clientIds);
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }
}
