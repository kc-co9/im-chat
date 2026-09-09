package com.co.kc.imchat.management.audit.sdk.transport.http;

import com.co.kc.imchat.management.audit.sdk.transport.AuditTransportException;
import com.co.kc.imchat.management.audit.sdk.model.AuditActor;
import com.co.kc.imchat.management.audit.sdk.model.AuditAttributes;
import com.co.kc.imchat.management.audit.sdk.model.AuditClientContext;
import com.co.kc.imchat.management.audit.sdk.model.AuditDescription;
import com.co.kc.imchat.management.audit.sdk.model.AuditEvent;
import com.co.kc.imchat.management.audit.sdk.model.AuditOutcome;
import com.co.kc.imchat.management.audit.sdk.model.AuditTarget;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import com.co.kc.imchat.management.audit.sdk.properties.AuditHttpProperties;
import com.co.kc.imchat.management.audit.sdk.support.AuditFailureReporter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpAuditTransportTest {

    @Test
    void retriesServerFailureAndKeepsBearerAuthentication() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AuditHttpProperties properties = properties(3, Duration.ZERO);
        server.expect(requestTo(properties.endpoint()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer access-1"))
                .andRespond(withServerError());
        server.expect(requestTo(properties.endpoint()))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        HttpAuditTransport transport = new HttpAuditTransport(
                builder.build(),
                () -> "access-1",
                properties,
                Runnable::run,
                reporter());

        transport.send(event());

        server.verify();
    }

    @Test
    void doesNotRetryContractFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AuditHttpProperties properties = properties(3, Duration.ZERO);
        server.expect(once(), requestTo(properties.endpoint()))
                .andRespond(withBadRequest());
        HttpAuditTransport transport = new HttpAuditTransport(
                builder.build(),
                () -> "access-1",
                properties,
                Runnable::run,
                reporter());

        assertThatThrownBy(() -> transport.send(event()))
                .isInstanceOf(AuditTransportException.class)
                .hasMessageContaining("rejected");
        server.verify();
    }

    @Test
    void rejectsSubmissionWhenExecutorQueueIsFull() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AuditAccessTokenProvider tokenProvider = () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return "access-1";
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1));
        HttpAuditTransport transport = new HttpAuditTransport(
                RestClient.create(),
                tokenProvider,
                properties(1, Duration.ZERO),
                executor,
                reporter());
        try {
            transport.send(event());
            started.await(1, TimeUnit.SECONDS);
            transport.send(event());

            assertThatThrownBy(() -> transport.send(event()))
                    .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private AuditHttpProperties properties(int attempts, Duration retryDelay) {
        return new AuditHttpProperties(
                URI.create("http://audit.internal/internal/audits"),
                Duration.ofSeconds(3),
                2,
                100,
                attempts,
                retryDelay);
    }

    private AuditEvent event() {
        return new AuditEvent(
                "audit-1",
                AuditType.BUSINESS,
                "USER_BAN",
                new AuditActor("ADMINISTRATOR", "1001", "admin"),
                new AuditTarget("USER", "2001"),
                AuditOutcome.SUCCESS,
                null,
                new AuditDescription("封禁普通用户"),
                new AuditClientContext("127.0.0.1", "JUnit"),
                "trace-1",
                new AuditAttributes(Map.of()),
                Instant.parse("2026-08-28T04:00:00Z"));
    }

    private AuditFailureReporter reporter() {
        return new AuditFailureReporter(new SimpleMeterRegistry());
    }
}
