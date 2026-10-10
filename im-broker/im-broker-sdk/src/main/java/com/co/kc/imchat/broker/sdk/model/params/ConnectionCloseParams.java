package com.co.kc.imchat.broker.sdk.model.params;

import com.co.kc.imchat.broker.sdk.access.BrokerCallerRole;
import com.co.kc.imchat.broker.sdk.access.ServiceDataPlaneRequest;
import com.co.kc.imchat.broker.sdk.routing.RoutableBrokerRequest;
import com.co.kc.imchat.broker.sdk.routing.RouteRequestContext;

import java.io.Serializable;

public record ConnectionCloseParams(Long userId,
                                    String sessionVersion,
                                    RouteRequestContext routeContext)
        implements Serializable, RoutableBrokerRequest<ConnectionCloseParams>, ServiceDataPlaneRequest {

    public ConnectionCloseParams(Long userId, String sessionVersion) {
        this(userId, sessionVersion, null);
    }

    public ConnectionCloseParams {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (sessionVersion == null || sessionVersion.isBlank()) {
            throw new IllegalArgumentException("sessionVersion must not be blank");
        }
    }

    @Override
    public ConnectionCloseParams withRouteContext(RouteRequestContext context) {
        return new ConnectionCloseParams(userId, sessionVersion, context);
    }

    @Override
    public BrokerCallerRole requiredCallerRole() {
        return BrokerCallerRole.ACCOUNT;
    }
}
