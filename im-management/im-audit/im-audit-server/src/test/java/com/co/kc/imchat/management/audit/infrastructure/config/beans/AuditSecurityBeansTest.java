package com.co.kc.imchat.management.audit.infrastructure.config.beans;

import com.co.kc.imchat.management.iam.sdk.properties.IamProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditSecurityBeansTest {

    @Test
    void providesOpaqueTokenIntrospectorForIngestionSecurityChain() {
        assertThat(Arrays.stream(AuditSecurityBeans.class.getDeclaredMethods()))
                .anyMatch(method -> method.getReturnType() == OpaqueTokenIntrospector.class);
    }

    @Test
    void appliesConfiguredReadTimeoutToIngestionIntrospection() throws Exception {
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/oauth2/introspect", exchange -> {
            try {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                requestReceived.countDown();
                releaseResponse.await();
                byte[] body = "{\"active\":false}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            IamProperties properties = properties(server.getAddress().getPort());
            OpaqueTokenIntrospector introspector =
                    new AuditSecurityBeans().auditOpaqueTokenIntrospector(properties);

            Future<?> introspection =
                    executor.submit(() -> introspector.introspect("opaque-token"));

            assertThat(requestReceived.await(1, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> introspection.get(1, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class);
            assertThat(authorization.get()).isEqualTo(
                    "Basic aW0tYXVkaXQlMkJjbGllbnQ6Y2xpZW50JTNBc2VjcmV0JTI1");
        } finally {
            releaseResponse.countDown();
            executor.shutdownNow();
            server.stop(0);
        }
    }

    private IamProperties properties(int port) {
        IamProperties.WebClient webClient = new IamProperties.WebClient(
                "im-audit+client",
                "client:secret%",
                URI.create("http://localhost:18091/iam/callback"),
                URI.create("http://localhost:18091/"));
        IamProperties.Application application = new IamProperties.Application(
                "imAudit",
                new IamProperties.Clients(webClient, null),
                new IamProperties.Session(
                        Base64.getEncoder().encodeToString(new byte[32]),
                        null,
                        false,
                        null,
                        null));
        return new IamProperties(
                true,
                URI.create("http://localhost:" + port),
                application,
                new IamProperties.Http(Duration.ofMillis(100), Duration.ofMillis(200)),
                null);
    }
}
