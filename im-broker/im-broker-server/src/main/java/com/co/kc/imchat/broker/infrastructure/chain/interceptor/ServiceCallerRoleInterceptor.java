package com.co.kc.imchat.broker.infrastructure.chain.interceptor;

import com.co.kc.imchat.broker.infrastructure.chain.BrokerRequestChain;
import com.co.kc.imchat.broker.infrastructure.chain.invocation.BrokerRequestInvocation;
import com.co.kc.imchat.broker.support.security.BrokerCallerPrincipal;
import com.co.kc.imchat.broker.sdk.access.BrokerAdmissionType;
import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.broker.sdk.access.ServiceDataPlaneRequest;

/**
 * 把服务数据面请求语义绑定到经过认证的调用方角色。
 */
public final class ServiceCallerRoleInterceptor implements BrokerRequestInterceptor {

    @Override
    public Object intercept(BrokerRequestInvocation<?> invocation,
                            BrokerRequestChain chain) throws Exception {
        if (invocation.operation().admissionType() != BrokerAdmissionType.SERVICE_DATA_PLANE) {
            return chain.proceed(invocation);
        }
        BrokerCallerPrincipal principal = invocation.principal();
        if (!(invocation.request() instanceof ServiceDataPlaneRequest request) || principal == null) {
            throw new SecurityException("service data-plane request identity is missing");
        }
        BrokerCallerRole requiredRole = request.requiredCallerRole();
        if (principal.callerRole() != requiredRole && principal.callerRole() != BrokerCallerRole.TRUSTED_NETWORK) {
            throw new SecurityException("service data-plane caller callerRole does not match request");
        }
        return chain.proceed(invocation);
    }
}
