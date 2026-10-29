package com.co.kc.imchat.gateway.ws.sdk;

import com.co.kc.imchat.broker.sdk.model.dto.GatewayEndpointDTO;
import com.co.kc.imchat.gateway.ws.sdk.model.result.GatewayFrameWriteResult;
import com.co.kc.imchat.gateway.ws.sdk.model.params.GatewayFrameWriteParams;
import com.co.kc.imchat.gateway.ws.sdk.model.params.ConnectionCloseParams;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltOperation;
import com.co.kc.imchat.gateway.ws.sdk.enums.GatewayBoltService;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.bolt.model.BoltTarget;
import com.co.kc.imchat.common.constant.PeerIdentityConstants;
import com.co.kc.imchat.plugin.bolt.model.enums.BoltTransportMode;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import com.co.kc.imchat.plugin.bolt.security.BoltDevelopmentValidator;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Broker 访问 WS 网关实时帧写入能力的客户端。
 * <p>
 * SDK 只负责按明确的网关地址发起 Bolt 调用，连接索引和路由决策由调用方维护。
 */
public class GatewayClient {
    private final BoltInvoker boltInvoker;
    private final int timeoutMillis;
    private final BoltTransportMode mode;
    private final Map<String, BoltTarget> developmentTargets;

    public GatewayClient(BoltInvoker boltInvoker, int timeoutMillis,
                         BoltTransportMode mode, List<BoltTarget> developmentTargets) {
        if (mode == null) {
            throw new IllegalArgumentException("Gateway mode is required");
        }
        this.mode = mode;
        Map<String, BoltTarget> targets = new HashMap<>();
        for (BoltTarget target : List.copyOf(developmentTargets)) {
            endpoint(target.address());
            validateIdentity(target.expectedPeerIdentity());
            if (targets.putIfAbsent(target.address(), target) != null) {
                throw new IllegalArgumentException("Duplicate Gateway development address");
            }
        }
        this.developmentTargets = Map.copyOf(targets);
        this.boltInvoker = boltInvoker;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * 向指定 WS 网关写入实时帧。
     *
     * @param gateway 目标 WS 网关
     * @param params 实时帧写入参数
     * @return 连接写入结果
     */
    public GatewayFrameWriteResult writeFrame(GatewayEndpointDTO gateway, GatewayFrameWriteParams params) {
        return boltInvoker.invoke(target(gateway), GatewayBoltService.FRAME.service(),
                GatewayBoltOperation.WRITE_FRAME.operation(), params,
                GatewayFrameWriteResult.class, timeoutMillis);
    }

    /** 关闭指定网关上的用户连接。 */
    public void closeConnections(GatewayEndpointDTO gateway, ConnectionCloseParams params) {
        boltInvoker.invoke(target(gateway), GatewayBoltService.CONNECTION.service(),
                GatewayBoltOperation.CLOSE_CONNECTIONS.operation(), params, Void.class, timeoutMillis);
    }

    private BoltTarget target(GatewayEndpointDTO gateway) {
        String host = gateway.host();
        String address = (host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host) + ":" + gateway.port();
        if (mode == BoltTransportMode.MTLS) {
            validateIdentity(gateway.workloadIdentity());
            return new BoltTarget(address, gateway.workloadIdentity());
        }
        URI endpoint = endpoint(address);
        String marker = gateway.workloadIdentity();
        if (marker == null || !marker.equals("trusted-network://" + endpoint.getHost())) {
            throw new IllegalArgumentException("Gateway development marker must match endpoint host exactly");
        }
        BoltTarget target = developmentTargets.get(address);
        if (target == null) {
            throw new IllegalArgumentException("Gateway development target has no deployment pair");
        }
        return target;
    }

    private void validateIdentity(String identity) {
        if (identity == null || !identity.startsWith(PeerIdentityConstants.GATEWAY_URI_PREFIX)
                || identity.length() == PeerIdentityConstants.GATEWAY_URI_PREFIX.length()) {
            throw new IllegalArgumentException("Gateway registered workload identity is missing or outside fixed namespace");
        }
        URI uri = URI.create(identity);
        if (!uri.isAbsolute() || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Gateway identity must be an absolute URI without query or fragment");
        }
    }

    private static URI endpoint(String address) {
        URI uri = URI.create("bolt://" + address);
        if (uri.getHost() == null || uri.getPort() < 1 || uri.getPort() > 65535 || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null || !uri.getRawPath().isEmpty()) {
            throw new IllegalArgumentException("Invalid Gateway development endpoint");
        }
        ImBoltProperties.Development development = new ImBoltProperties.Development();
        development.setIsolatedContainerNetwork(true);
        BoltDevelopmentValidator.validateAddress(uri.getHost(), development);
        return uri;
    }
}
