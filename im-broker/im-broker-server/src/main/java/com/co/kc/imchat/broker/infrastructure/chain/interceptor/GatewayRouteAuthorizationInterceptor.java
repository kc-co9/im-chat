package com.co.kc.imchat.broker.infrastructure.chain.interceptor;

import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestChain;

import com.co.kc.imchat.broker.domain.connection.model.GatewayId;

import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.broker.sdk.access.BrokerAdmissionType;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.broker.sdk.access.GatewayRouteRequest;

import java.time.Duration;
import java.time.Instant;

/** 校验 Gateway 请求身份与已提交注册租约绑定。 */
public final class GatewayRouteAuthorizationInterceptor implements BrokerRequestInterceptor {
    private final GatewayRegistry gatewayRegistry;
    private final Duration gatewayTtl;
    public GatewayRouteAuthorizationInterceptor(GatewayRegistry gatewayRegistry,
                                         Duration gatewayTtl) {
        if (gatewayRegistry == null || gatewayTtl == null || gatewayTtl.isZero()
                || gatewayTtl.isNegative()) {
            throw new IllegalArgumentException("invalid Gateway route access configuration");
        }
        this.gatewayRegistry = gatewayRegistry;
        this.gatewayTtl = gatewayTtl;
    }

    @Override
    public Object intercept(BrokerRequestInvocation<?> invocation,
                            BrokerRequestChain chain) throws Exception {
        if (invocation.operation().admissionType() != BrokerAdmissionType.GATEWAY_ROUTE) {
            return chain.proceed(invocation);
        }
        BrokerCallerPrincipal principal = invocation.principal();
        if (!(invocation.request() instanceof GatewayRouteRequest request)
                || principal == null
                || (principal.callerRole() != BrokerCallerRole.GATEWAY
                && principal.callerRole() != BrokerCallerRole.TRUSTED_NETWORK)) {
            throw new SecurityException("Gateway route request identity is invalid");
        }
        boolean authorized = gatewayRegistry.find(new GatewayId(request.gatewayId()))
                .filter(endpoint -> endpoint.principalId().value().equals(principal.callerIdentity()))
                .filter(endpoint -> Instant.now().toEpochMilli() - endpoint.lastSeenAt().toEpochMilli() < gatewayTtl.toMillis())
                .isPresent();
        if (!authorized) {
            throw new SecurityException("Gateway route lease is missing or expired");
        }
        return chain.proceed(invocation);
    }
}
