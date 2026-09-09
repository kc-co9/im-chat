package com.co.kc.imchat.management.audit.sdk.transport.http;

import com.co.kc.imchat.management.audit.sdk.model.AuditEvent;
import com.co.kc.imchat.management.audit.sdk.properties.AuditHttpProperties;
import com.co.kc.imchat.management.audit.sdk.support.AuditFailureReporter;
import com.co.kc.imchat.management.audit.sdk.transport.AuditTransport;
import com.co.kc.imchat.management.audit.sdk.transport.AuditTransportException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.concurrent.Executor;

/** 使用有界执行器异步投递 Audit 内部 HTTP 请求。 */
@RequiredArgsConstructor
public class HttpAuditTransport implements AuditTransport {
    private static final String HTTP_STAGE = "http";

    private final RestClient restClient;
    private final AuditAccessTokenProvider tokenProvider;
    private final AuditHttpProperties properties;
    private final Executor executor;
    private final AuditFailureReporter failureReporter;

    @Override
    public void send(AuditEvent event) {
        executor.execute(() -> deliver(event));
    }

    private void deliver(AuditEvent event) {
        try {
            executeWithRetry(event);
        } catch (RuntimeException failure) {
            failureReporter.report(HTTP_STAGE, failure);
            throw failure;
        }
    }

    private void executeWithRetry(AuditEvent event) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                restClient.post()
                        .uri(properties.endpoint())
                        .headers(headers -> headers.setBearerAuth(tokenProvider.accessToken()))
                        .body(event)
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (HttpClientErrorException failure) {
                throw new AuditTransportException(
                        "Audit HTTP request was rejected",
                        failure);
            } catch (HttpServerErrorException | ResourceAccessException failure) {
                lastFailure = failure;
                if (attempt < properties.maxAttempts()) {
                    waitBeforeRetry();
                }
            }
        }
        throw new AuditTransportException("Audit HTTP delivery failed", lastFailure);
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(properties.retryDelay());
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AuditTransportException("Audit HTTP retry was interrupted", failure);
        }
    }
}
