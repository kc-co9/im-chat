package com.co.kc.imchat.broker.adapter.gateway;

import com.co.kc.imchat.broker.domain.connection.model.GatewayHost;
import com.co.kc.imchat.broker.domain.connection.model.GatewayId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayPort;
import com.co.kc.imchat.broker.domain.connection.model.GatewayPrincipalId;
import com.co.kc.imchat.broker.domain.connection.model.GatewayRegistration;
import com.co.kc.imchat.broker.model.cqrs.command.FrameDispatchCommand;
import com.co.kc.imchat.common.model.enums.FrameType;
import com.co.kc.imchat.common.model.io.FrameResponse;
import com.co.kc.imchat.gateway.ws.sdk.GatewayClient;
import com.co.kc.imchat.gateway.ws.sdk.model.params.GatewayFrameWriteParams;
import com.co.kc.imchat.gateway.ws.sdk.model.result.GatewayFrameWriteResult;
import com.co.kc.imchat.plugin.metrics.support.MetricsCollector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayDispatchAdapterTest {
    @Test
    void writesFrameAndRecordsPerTargetSuccessAndFailure() {
        GatewayClient client = mock(GatewayClient.class);
        when(client.writeFrame(any(), any()))
                .thenReturn(new GatewayFrameWriteResult(List.of("connection-a"), List.of()))
                .thenThrow(new IllegalStateException("gateway unavailable"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayDispatchAdapter adapter = new GatewayDispatchAdapter(client, Runnable::run,
                new MetricsCollector(registry));
        GatewayRegistration gateway = new GatewayRegistration(new GatewayId("gateway-a"),
                new GatewayHost("gateway.example"), new GatewayPort(18012),
                new GatewayPrincipalId("identity-a"), Instant.EPOCH, Instant.EPOCH);
        FrameResponse frame = new FrameResponse("1", FrameType.PUSH, "message.private.sent",
                null, null, null, null, Map.of());
        FrameDispatchCommand command = new FrameDispatchCommand(42L, frame, "delivery-1", 7L);

        assertThat(adapter.dispatch(gateway, command).toCompletableFuture().join())
                .extracting("connectionId").containsExactly("connection-a");
        assertThatThrownBy(() -> adapter.dispatch(gateway, command).toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalStateException.class);
        ArgumentCaptor<GatewayFrameWriteParams> requests = ArgumentCaptor.forClass(GatewayFrameWriteParams.class);
        verify(client, times(2)).writeFrame(any(), requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.userId()).isEqualTo(42L);
            assertThat(request.deliveryId()).isEqualTo("delivery-1");
            assertThat(request.deliveryAttemptId()).isNotBlank();
            assertThat(request.frame()).isSameAs(frame);
        });
        assertThat(registry.counter("im.broker.gateway.frame.write", "outcome", "success").count()).isEqualTo(1);
        assertThat(registry.counter("im.broker.gateway.frame.write", "outcome", "failure").count()).isEqualTo(1);
        assertThat(registry.timer("im.broker.gateway.frame.write.duration").count()).isEqualTo(2);
    }
}
