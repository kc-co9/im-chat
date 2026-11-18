package com.co.kc.imchat.management.ops.cli.config;

import com.co.kc.imchat.common.utils.JsonUtils;

import java.io.IOException;

/** CLI 启动配置；只保存 IAM、Ops、回调端口和 OAuth scope 等非敏感参数。 */
public record CliConfiguration(
        /* IAM 服务 HTTPS origin。 */
        String iamOrigin,
        /* Ops 服务 HTTPS origin。 */
        String opsOrigin,
        /* OAuth public Client ID。 */
        String clientId,
        /* 固定 IPv4 loopback 回调端口。 */
        Integer callbackPort,
        /* OAuth 授权 scope。 */
        String scope) {

    /** 从严格 JSON 对象读取配置，并拒绝缺失、空值和越界端口。 */
    public static CliConfiguration read(byte[] body) throws IOException {
        CliConfiguration configuration = JsonUtils.fromStrictJson(body, CliConfiguration.class);
        if (configuration == null || configuration.iamOrigin == null || configuration.iamOrigin.isBlank()
                || configuration.opsOrigin == null || configuration.opsOrigin.isBlank()
                || configuration.clientId == null || configuration.clientId.isBlank()
                || configuration.scope == null || configuration.scope.isBlank()
                || configuration.callbackPort == null || configuration.callbackPort < 1
                || configuration.callbackPort > 65535) {
            throw new IOException("Invalid CLI configuration");
        }
        return configuration;
    }
}
