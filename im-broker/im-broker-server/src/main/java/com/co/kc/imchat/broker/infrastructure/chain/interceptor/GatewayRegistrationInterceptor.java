package com.co.kc.imchat.broker.infrastructure.chain.interceptor;

import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestChain;

import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.broker.sdk.access.BrokerAdmissionType;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.broker.sdk.access.GatewayRouteRequest;

/** Gateway bootstrap 准入校验；已认证调用方身份沿责任链传递。 */
public final class GatewayRegistrationInterceptor implements BrokerRequestInterceptor {

    @Override
    public Object intercept(BrokerRequestInvocation<?> invocation,
                            BrokerRequestChain chain) throws Exception {
        if (invocation.operation().admissionType() != BrokerAdmissionType.GATEWAY_BOOTSTRAP) {
            return chain.proceed(invocation);
        }
        BrokerCallerPrincipal principal = invocation.principal();
        if (!(invocation.request() instanceof GatewayRouteRequest request)
                || request.gatewayId() == null || request.gatewayId().isBlank()
                || principal == null
                || (principal.callerRole() != BrokerCallerRole.GATEWAY
                && principal.callerRole() != BrokerCallerRole.TRUSTED_NETWORK)) {
            throw new SecurityException("Gateway registration identity is invalid");
        }
        return chain.proceed(invocation);
    }
}
