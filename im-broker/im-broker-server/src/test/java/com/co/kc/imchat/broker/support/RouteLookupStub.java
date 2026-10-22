package com.co.kc.imchat.broker.support;

import com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter;
import com.co.kc.imchat.common.domain.user.model.UserId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import java.time.Instant;
import com.alicp.jetcache.Cache;
import java.util.Map;
import java.util.LinkedHashMap;
import static org.mockito.Mockito.doAnswer;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.model.cqrs.result.RouteLocation;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 测试用受控 peer 响应，生产代码直接调用 Adapter，不保留 Client 接口。 */
@FunctionalInterface
public interface RouteLookupStub {
    /** 测试线程上的短 TTL 标记替身，真实 JetCache 过期和容量由 im-cache 集成测试验证。 */
    static Cache<UserId, Boolean> negativeCache(Duration retention) {
        Cache<UserId, Boolean> cache = mock(Cache.class);
        Map<UserId, Instant> until = new LinkedHashMap<>();
        doAnswer(invocation -> {
            until.put(invocation.getArgument(0), Instant.now().plus(retention));
            return null;
        }).when(cache).put(any(), any());
        when(cache.get(any())).thenAnswer(invocation -> {
            Instant expiresAt = until.get(invocation.getArgument(0));
            return expiresAt != null && Instant.now().isBefore(expiresAt) ? true : null;
        });
        doAnswer(invocation -> until.remove(invocation.getArgument(0)) != null)
                .when(cache).remove(any());
        return cache;
    }

    CompletionStage<List<RouteLocation>> lookup(BrokerNode broker,
            UserId userId, RoutingEpoch routingEpoch);

    default BrokerPeerAdapter peerAdapter() {
        BrokerPeerAdapter adapter = mock(BrokerPeerAdapter.class);
        when(adapter.lookupRoutes(any(BrokerNode.class), any(UserId.class), any(RoutingEpoch.class)))
                .thenAnswer(invocation -> {
            try {
                return lookup(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
            } catch (RuntimeException failure) {
                return java.util.concurrent.CompletableFuture.failedFuture(failure);
            }
        });
        return adapter;
    }

}
