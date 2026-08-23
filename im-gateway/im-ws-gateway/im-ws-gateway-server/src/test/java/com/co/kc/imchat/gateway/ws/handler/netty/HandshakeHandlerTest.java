package com.co.kc.imchat.gateway.ws.handler.netty;

import com.co.kc.imchat.broker.sdk.model.params.ConnectionRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.GatewayRegisterParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionSyncParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerFrameWriteParams;
import com.co.kc.imchat.broker.sdk.model.params.ConnectionUnregisterParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerFrameWriteResult;
import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.gateway.ws.server.context.ContextAttributes;
import com.co.kc.imchat.gateway.ws.support.BrokerClientTestSupport;
import com.co.kc.imchat.common.model.enums.ServiceName;
import com.co.kc.imchat.broker.sdk.enums.BrokerLoadBalance;
import com.co.kc.imchat.gateway.ws.registry.ConnectionRegistry;
import com.co.kc.imchat.gateway.ws.security.authentication.WsAuthenticationManager;
import com.co.kc.imchat.gateway.ws.security.identity.WsPrincipal;
import com.co.kc.imchat.gateway.ws.server.handler.HandshakeHandler;
import com.co.kc.imchat.service.account.facade.AccountService;
import com.co.kc.imchat.service.account.facade.dto.SessionAuthDTO;
import com.co.kc.imchat.service.account.facade.params.AccessTokenParams;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HandshakeHandlerTest {

    @Test
    void registersConnectionWithTokenUserIdInsteadOfQueryUserId() {
        CapturingConnectionRegistrationAdapter brokerClient = new CapturingConnectionRegistrationAdapter();
        ConnectionRegistry registry = new ConnectionRegistry();
        WsAuthenticationManager authenticationManager = authenticationManager("valid-token", 42L);
        EmbeddedChannel channel = new EmbeddedChannel(new HandshakeHandler(
                "gw-1", "/ws", brokerClient, registry, authenticationManager));
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/ws?token=valid-token&userId=99&deviceId=d1&platform=web");

        channel.writeInbound(request);

        WsPrincipal principal = channel.attr(ContextAttributes.PRINCIPAL).get();
        assertEquals(42L, principal.userId());
        assertEquals("session-v1", principal.sessionVersion());
        assertEquals(42L, brokerClient.registerConnectionCommand.userId());
        assertEquals("gw-1", brokerClient.registerConnectionCommand.gatewayId());
        assertEquals("/ws", request.uri());
    }

    @Test
    void rejectsConnectionWithUnauthorizedWhenTokenIsInvalid() {
        CapturingConnectionRegistrationAdapter brokerClient = new CapturingConnectionRegistrationAdapter();
        ConnectionRegistry registry = new ConnectionRegistry();
        WsAuthenticationManager authenticationManager = authenticationManager("valid-token", 42L);
        EmbeddedChannel channel = new EmbeddedChannel(new HandshakeHandler(
                "gw-1", "/ws", brokerClient, registry, authenticationManager));
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/ws?token=bad-token&userId=99");

        channel.writeInbound(request);

        assertNull(brokerClient.registerConnectionCommand);
        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.UNAUTHORIZED, response.status());
        response.release();
        assertEquals(0, request.refCnt());
        assertFalse(channel.isOpen());
    }

    @Test
    void acceptsBearerTokenFromAuthorizationHeader() {
        CapturingConnectionRegistrationAdapter brokerClient = new CapturingConnectionRegistrationAdapter();
        ConnectionRegistry registry = new ConnectionRegistry();
        WsAuthenticationManager authenticationManager = authenticationManager("valid-token", 42L);
        EmbeddedChannel channel = new EmbeddedChannel(new HandshakeHandler(
                "gw-1", "/ws", brokerClient, registry, authenticationManager));
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/ws");
        request.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer valid-token ");

        channel.writeInbound(request);

        WsPrincipal principal = channel.attr(ContextAttributes.PRINCIPAL).get();
        assertEquals(42L, principal.userId());
        assertEquals("session-v1", principal.sessionVersion());
        assertEquals(42L, brokerClient.registerConnectionCommand.userId());
    }

    @Test
    void rejectsConnectionWhenPathDoesNotMatchConfiguredWebSocketPath() {
        CapturingConnectionRegistrationAdapter brokerClient = new CapturingConnectionRegistrationAdapter();
        ConnectionRegistry registry = new ConnectionRegistry();
        WsAuthenticationManager authenticationManager = authenticationManager("valid-token", 42L);
        EmbeddedChannel channel = new EmbeddedChannel(new HandshakeHandler(
                "gw-1", "/ws", brokerClient, registry, authenticationManager));
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/connect?token=valid-token");

        channel.writeInbound(request);

        assertNull(brokerClient.registerConnectionCommand);
        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.NOT_FOUND, response.status());
        response.release();
        assertEquals(0, request.refCnt());
        assertFalse(channel.isOpen());
    }

    @Test
    void rejectsConnectionAndCleansRegistryWhenBrokerRegistrationFails() {
        CapturingConnectionRegistrationAdapter brokerClient = new CapturingConnectionRegistrationAdapter();
        brokerClient.failRegisterConnection = true;
        ConnectionRegistry registry = new ConnectionRegistry();
        WsAuthenticationManager authenticationManager = authenticationManager("valid-token", 42L);
        EmbeddedChannel channel = new EmbeddedChannel(new HandshakeHandler(
                "gw-1", "/ws", brokerClient, registry, authenticationManager));
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/ws?token=valid-token");

        channel.writeInbound(request);

        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.SERVICE_UNAVAILABLE, response.status());
        response.release();
        assertEquals(0, request.refCnt());
        assertFalse(channel.isOpen());
        assertEquals(List.of(), registry.activeConnectionIds());
    }

    @Test
    void cleansLocalConnectionWhenBrokerUnregisterFails() {
        CapturingConnectionRegistrationAdapter brokerClient = new CapturingConnectionRegistrationAdapter();
        brokerClient.failUnregisterConnection = true;
        ConnectionRegistry registry = new ConnectionRegistry();
        WsAuthenticationManager authenticationManager = authenticationManager("valid-token", 42L);
        EmbeddedChannel channel = new EmbeddedChannel(new HandshakeHandler(
                "gw-1", "/ws", brokerClient, registry, authenticationManager));
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/ws?token=valid-token");
        channel.writeInbound(request);

        assertDoesNotThrow(() -> channel.close());

        assertEquals(List.of(), registry.activeConnectionIds());
    }

    @Test
    void pendingAuthenticationDoesNotBlockEventLoopAndReleasesRejectedRequestOnce() {
        WsAuthenticationManager authenticationManager = mock(WsAuthenticationManager.class);
        CompletableFuture<WsPrincipal> pendingAuthentication = new CompletableFuture<>();
        when(authenticationManager.authenticate("pending-token")).thenReturn(pendingAuthentication);
        EmbeddedChannel channel = new EmbeddedChannel(new HandshakeHandler(
                "gw-1", "/ws", new CapturingConnectionRegistrationAdapter(),
                new ConnectionRegistry(), authenticationManager));
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/ws?token=pending-token");

        channel.writeInbound(request);
        AtomicBoolean eventLoopResponsive = new AtomicBoolean();
        channel.eventLoop().execute(() -> eventLoopResponsive.set(true));
        channel.runPendingTasks();

        assertEquals(true, eventLoopResponsive.get());
        assertEquals(1, request.refCnt());

        pendingAuthentication.complete(null);
        channel.runPendingTasks();

        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.UNAUTHORIZED, response.status());
        response.release();
        assertEquals(0, request.refCnt());
    }

    private WsAuthenticationManager authenticationManager(String validToken, Long userId) {
        AccountService accountService = mock(AccountService.class);
        when(accountService.authenticate(new AccessTokenParams(validToken)))
                .thenReturn(new SessionAuthDTO(
                        userId, "session-v1", Instant.parse("2026-08-16T12:00:00Z")));
        when(accountService.authenticate(new AccessTokenParams("bad-token")))
                .thenThrow(new com.co.kc.imchat.common.exception.AuthException("Access Token 或会话无效"));
        return new WsAuthenticationManager(accountService, Runnable::run, Duration.ofSeconds(1));
    }

    private static class CapturingConnectionRegistrationAdapter extends BrokerClient {
        private ConnectionRegisterParams registerConnectionCommand;
        private boolean failRegisterConnection;
        private boolean failUnregisterConnection;

        private CapturingConnectionRegistrationAdapter() {
            super(BrokerClientTestSupport.invoker(), BrokerClientTestSupport.discovery(),
                    ServiceName.IM_BROKER, BrokerLoadBalance.HASH, 3000);
        }

        @Override
        public void registerGateway(GatewayRegisterParams command) {
        }

        @Override
        public void syncConnections(ConnectionSyncParams command) {
        }

        @Override
        public void registerConnection(ConnectionRegisterParams command) {
            if (failRegisterConnection) {
                throw new IllegalStateException("broker unavailable");
            }
            this.registerConnectionCommand = command;
        }

        @Override
        public void unregisterConnection(ConnectionUnregisterParams command) {
            if (failUnregisterConnection) {
                throw new IllegalStateException("broker unavailable");
            }
        }

        @Override
        public BrokerFrameWriteResult writeFrame(BrokerFrameWriteParams command) {
            return BrokerFrameWriteResult.ok();
        }
    }
}
