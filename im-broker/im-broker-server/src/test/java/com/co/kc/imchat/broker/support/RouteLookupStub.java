package com.co.kc.imchat.broker.support;

import com.co.kc.imchat.broker.adapter.cluster.BrokerClusterAdapter;
import com.co.kc.imchat.broker.domain.connection.model.UserId;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import java.time.Instant;
import com.alicp.jetcache.Cache;
import java.util.Map;
import java.util.LinkedHashMap;
import static org.mockito.Mockito.doAnswer;
import com.co.kc.imchat.broker.sdk.cluster.BrokerNode;
import com.co.kc.imchat.broker.model.cqrs.query.BrokerRouteLookupParams;
import com.co.kc.imchat.broker.model.cqrs.result.RouteLocation;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRegistration;
import com.co.kc.imchat.broker.model.cqrs.command.FrameDispatchCommand;
import com.co.kc.imchat.gateway.ws.sdk.model.params.GatewayFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.dto.BrokerFrameWriteDTO;
import com.co.kc.imchat.broker.sdk.enums.BrokerFrameWriteStatus;
import com.co.kc.imchat.broker.transformer.infrastructure.RegistryTransformer;

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

    default BrokerClusterAdapter adapter() {
        BrokerClusterAdapter adapter = mock(BrokerClusterAdapter.class);
        when(adapter.lookupRoutes(any(BrokerRouteLookupParams.class))).thenAnswer(invocation -> {
            try {
                BrokerRouteLookupParams params = invocation.getArgument(0);
                return lookup(params.broker(), params.userId(), params.routingEpoch());
            } catch (RuntimeException failure) {
                return java.util.concurrent.CompletableFuture.failedFuture(failure);
            }
        });
        return adapter;
    }

    /** 保留测试中的 Gateway 请求记录，生产映射与指标另由真实 Adapter 回归验证。 */
    default BrokerClusterAdapter adapter(GatewayClient gatewayClient) {
        return adapter(gatewayClient, Runnable::run);
    }

    default BrokerClusterAdapter adapter(GatewayClient gatewayClient, java.util.concurrent.Executor executor) {
        BrokerClusterAdapter adapter = adapter();
        when(adapter.dispatchFrame(any(), any())).thenAnswer(invocation -> {
            GatewayRegistration gateway = invocation.getArgument(0);
            FrameDispatchCommand command = invocation.getArgument(1);
            return java.util.concurrent.CompletableFuture.supplyAsync(() ->
                    gatewayClient.writeFrame(RegistryTransformer.INSTANCE.gatewayEndpointFrom(gateway),
                            new GatewayFrameWriteParams(command.userId(), command.deliveryId(), "test-attempt", command.frame()))
                            .writeList().stream().map(write -> new BrokerFrameWriteDTO(
                                    write.connectionId(), BrokerFrameWriteStatus.valueOf(write.status().name()))).toList(), executor);
        });
        return adapter;
    }
}
