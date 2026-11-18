package com.co.kc.imchat.management.ops.cli;

import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 仅测试子进程使用：在真实入口等待写响应时触发 Java 线程中断。
 */
public final class InterruptedOpsCliLauncher {

    public static void main(String[] args) throws Exception {
        Thread caller = Thread.currentThread();
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicInteger writes = new AtomicInteger();
        CompletableFuture<HttpResponse<byte[]>> pending = new CompletableFuture<>() {
            @Override
            public HttpResponse<byte[]> get(long timeout, TimeUnit unit)
                    throws InterruptedException, java.util.concurrent.ExecutionException,
                    java.util.concurrent.TimeoutException {
                waiting.countDown();
                return super.get(timeout, unit);
            }
        };
        HttpClient http = mock(HttpClient.class);
        when(http.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
        when(http.connectTimeout()).thenReturn(Optional.of(Duration.ofSeconds(10)));
        HttpClient.Builder builder = mock(HttpClient.Builder.class);
        when(builder.followRedirects(HttpClient.Redirect.NEVER)).thenReturn(builder);
        when(builder.connectTimeout(Duration.ofSeconds(10))).thenReturn(builder);
        when(builder.build()).thenReturn(http);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> token = mock(HttpResponse.class);
        when(token.statusCode()).thenReturn(200);
        when(token.body()).thenReturn(
                "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                        .getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> revoke = mock(HttpResponse.class);
        when(revoke.statusCode()).thenReturn(503);
        when(revoke.body()).thenReturn(new byte[0]);
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    if (request.uri().getPath().equals("/oauth2/token")) {
                        return CompletableFuture.completedFuture(token);
                    }
                    if (request.uri().getPath().equals("/oauth2/revoke")) {
                        return CompletableFuture.completedFuture(revoke);
                    }
                    writes.incrementAndGet();
                    return pending;
                });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.writeString(Path.of(args[1]), writes.get() + "," + pending.isCancelled());
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }));
        Thread interrupter = new Thread(() -> {
            try {
                if (waiting.await(15, TimeUnit.SECONDS)) {
                    caller.interrupt();
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
            }
        });
        interrupter.setDaemon(true);
        interrupter.start();
        try (MockedStatic<HttpClient> factory = mockStatic(HttpClient.class)) {
            factory.when(HttpClient::newBuilder).thenReturn(builder);
            OpsCli.main(new String[]{"--config", args[0]});
        }
    }
}
