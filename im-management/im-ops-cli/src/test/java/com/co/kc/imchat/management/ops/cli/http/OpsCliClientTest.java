package com.co.kc.imchat.management.ops.cli.http;

import org.junit.jupiter.api.Test;
import com.co.kc.imchat.management.ops.cli.auth.CliLoginSession;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.net.CookieManager;
import java.net.http.HttpTimeoutException;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import java.net.URI;
import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;

class OpsCliClientTest {
    @Test
    void standardScopeFieldIsAcceptedInTokenResponse() throws Exception {
        com.co.kc.imchat.management.ops.cli.model.io.IamTokenResponse response =
                com.co.kc.imchat.management.ops.cli.model.io.IamTokenResponse.read(
                        ("{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\","
                                + "\"expires_in\":300,\"scope\":\"ops:broker:read\"}")
                                .getBytes(StandardCharsets.UTF_8));
        assertEquals("opaque-fixture", response.accessToken());
    }


    private static HttpClient transport() {
        HttpClient http = mock(HttpClient.class);
        when(http.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
        when(http.connectTimeout()).thenReturn(Optional.of(Duration.ofSeconds(10)));
        clearInvocations(http);
        return http;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {401, 403})
    void authorizationRejectionIsNotUnknownOrAutomaticallyReplayed(int status,
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> token = mock(HttpResponse.class);
        when(token.statusCode()).thenReturn(200);
        when(token.body()).thenReturn(
                "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                        .getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> rejected = mock(HttpResponse.class);
        when(rejected.statusCode()).thenReturn(status);
        when(rejected.body()).thenReturn(new byte[0]);
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(token), CompletableFuture.completedFuture(rejected));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);
        client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                URI.create("http://127.0.0.1:18881/oauth/callback"));
        com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.Request request =
                new com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.Request(
                        "https://ops.example.test", "original-operation", 17, "broker-1", "promote", "broker-1:18081", "approved");
        java.nio.file.Path record = directory.resolve("request.json");

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> client.submit(record, request));

        assertEquals(status == 401 ? "Login is required" : "Ops permission denied", failure.getMessage());
        assertEquals(request, com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.read(record));
        verify(http, times(2)).sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        clearInvocations(http);
        if (status == 401) {
            assertThrows(IllegalStateException.class, client::queryCluster);
            verifyNoInteractions(http);
        }
    }

    @Test
    void queryUnauthorizedClearsLocalSessionBeforeNextCommand() throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> token = mock(HttpResponse.class);
        when(token.statusCode()).thenReturn(200);
        when(token.body()).thenReturn(
                "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                        .getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> unauthorized = mock(HttpResponse.class);
        when(unauthorized.statusCode()).thenReturn(401);
        when(unauthorized.body()).thenReturn(new byte[0]);
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(token), CompletableFuture.completedFuture(unauthorized));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);
        client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                URI.create("http://127.0.0.1:18881/oauth/callback"));

        IllegalStateException failure = assertThrows(IllegalStateException.class, client::queryCluster);

        assertEquals("Login is required", failure.getMessage());
        clearInvocations(http);
        assertThrows(IllegalStateException.class, client::queryCluster);
        verifyNoInteractions(http);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"{}", "{\"operationId\":\"other\",\"status\":\"APPLIED\"}",
            "{\"operationId\":\"original-operation\"}", "{\"operationId\":\"original-operation\",\"status\":\"SUCCEEDED\"}"})
    void uncorrelatedSubmissionCannotBeReportedAsSuccess(String data,
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> token = mock(HttpResponse.class);
        when(token.statusCode()).thenReturn(200);
        when(token.body()).thenReturn(
                "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                        .getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> submitted = mock(HttpResponse.class);
        when(submitted.statusCode()).thenReturn(200);
        when(submitted.body()).thenReturn(("{\"code\":0,\"data\":" + data + "}").getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(token), CompletableFuture.completedFuture(submitted));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);
        client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                URI.create("http://127.0.0.1:18881/oauth/callback"));
        com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.Request request =
                new com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.Request(
                        "https://ops.example.test", "original-operation", 17, "broker-1", "promote", "broker-1:18081", "approved");

        com.fasterxml.jackson.databind.JsonNode result = client.submit(directory.resolve("request.json"), request);

        assertEquals("original-operation", result.path("operationId").asText());
        assertEquals("UNKNOWN", result.path("status").asText());
    }

    @Test
    void lostSubmissionResponseReturnsUnknownAndKeepsOriginalRecord(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
            throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> token = mock(HttpResponse.class);
        when(token.statusCode()).thenReturn(200);
        when(token.body()).thenReturn(
                "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                        .getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(token))
                .thenReturn(CompletableFuture.failedFuture(new IOException("connection-lost-fixture")));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);
        client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                URI.create("http://127.0.0.1:18881/oauth/callback"));
        java.nio.file.Path record = directory.resolve("original.json");
        com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.Request original =
                new com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.Request(
                        "https://ops.example.test", "original-operation", 17, "broker-1", "promote", "broker-1:18081", "approved");

        com.fasterxml.jackson.databind.JsonNode result = assertDoesNotThrow(() -> client.submit(record, original));

        assertEquals("original-operation", result.path("operationId").asText());
        assertEquals("UNKNOWN", result.path("status").asText());
        assertEquals(original, com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.read(record));
        verify(http, times(2)).sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
    }

    @Test
    void explicitRetryUsesExistingRecordWithoutOverwriting(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
            throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> token = mock(HttpResponse.class);
        when(token.statusCode()).thenReturn(200);
        when(token.body()).thenReturn(
                "{\"access_token\":\"current-admin-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                        .getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> accepted = mock(HttpResponse.class);
        when(accepted.statusCode()).thenReturn(200);
        when(accepted.body()).thenReturn(
                "{\"code\":0,\"data\":{\"operationId\":\"original-operation\",\"status\":\"APPLIED\"}}"
                        .getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(token), CompletableFuture.completedFuture(accepted));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);
        client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                URI.create("http://127.0.0.1:18881/oauth/callback"));
        java.nio.file.Path record = directory.resolve("original.json");
        com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.Request original =
                new com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.Request(
                        "https://ops.example.test", "original-operation", 17, "broker-1", "promote", "broker-1:18081", "approved");
        com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.write(record, original);
        byte[] before = java.nio.file.Files.readAllBytes(record);

        com.fasterxml.jackson.databind.JsonNode result = assertDoesNotThrow(() -> client.retry(record));

        assertEquals("original-operation", result.path("operationId").asText());
        org.junit.jupiter.api.Assertions.assertArrayEquals(before, java.nio.file.Files.readAllBytes(record));
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).sendAsync(requests.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        assertEquals("Bearer current-admin-fixture", requests.getAllValues().get(1).headers()
                .firstValue("Authorization").orElseThrow());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"add-learner", "complete-learner-sync", "promote", "demote", "remove"})
    void membershipIsRecordedBeforeSendingOriginalSemantics(String action,
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> token = mock(HttpResponse.class);
        when(token.statusCode()).thenReturn(200);
        when(token.body()).thenReturn(
                "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                        .getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(token));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);
        client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                URI.create("http://127.0.0.1:18881/oauth/callback"));
        clearInvocations(http);
        com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.Request request =
                new com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.Request(
                        "https://ops.example.test", "original-operation", 17, "broker-1", action,
                        "broker-1:18081",
                        action.equals("add-learner") ? null : "approved");
        java.nio.file.Path record = directory.resolve("request.json");
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenAnswer(invocation -> {
                    assertEquals(request, com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.read(record));
                    HttpRequest sent = invocation.getArgument(0);
                    assertEquals("POST", sent.method());
                    assertEquals(URI.create("https://ops.example.test/api/cli/brokers/broker-1/raft/" + action), sent.uri());
                    HttpResponse.BodySubscriber<byte[]> requestBody = HttpResponse.BodySubscribers.ofByteArray();
                    sent.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
                        public void onSubscribe(Flow.Subscription subscription) { requestBody.onSubscribe(subscription); }
                        public void onNext(ByteBuffer item) { requestBody.onNext(List.of(item)); }
                        public void onError(Throwable failure) { requestBody.onError(failure); }
                        public void onComplete() { requestBody.onComplete(); }
                    });
                    com.fasterxml.jackson.databind.JsonNode json = new com.fasterxml.jackson.databind.json.JsonMapper()
                            .readTree(requestBody.getBody().toCompletableFuture().get(1, TimeUnit.SECONDS));
                    assertEquals("original-operation", json.path("operationId").asText());
                    assertEquals(17, json.path("expectedEpoch").longValue());
                    assertEquals(action.equals("add-learner") ? 3 : 4, json.size());
                    if (action.equals("add-learner")) {
                        assertEquals("broker-1:18081", json.path("membershipEndpoint").asText());
                        assertFalse(json.has("reason"));
                    } else {
                        assertEquals("approved", json.path("reason").asText());
                        assertEquals("broker-1:18081", json.path("membershipEndpoint").asText());
                    }
                    assertEquals("Bearer opaque-fixture", sent.headers().firstValue("Authorization").orElseThrow());
                    @SuppressWarnings("unchecked")
                    HttpResponse<byte[]> accepted = mock(HttpResponse.class);
                    when(accepted.statusCode()).thenReturn(200);
                    when(accepted.body()).thenReturn(
                            "{\"code\":0,\"data\":{\"operationId\":\"original-operation\",\"status\":\"APPLIED\"}}"
                                    .getBytes(StandardCharsets.UTF_8));
                    return CompletableFuture.completedFuture(accepted);
                });

        com.fasterxml.jackson.databind.JsonNode result = client.submit(record, request);

        org.junit.jupiter.api.Assertions.assertNotNull(result);
        assertEquals("APPLIED", result.path("status").asText());
        assertEquals("original-operation", result.path("operationId").asText());
        verify(http).sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        {
            byte[] original = java.nio.file.Files.readAllBytes(record);
            clearInvocations(http);
            assertEquals("APPLIED", client.retry(record).path("status").asText());
            org.junit.jupiter.api.Assertions.assertArrayEquals(original, java.nio.file.Files.readAllBytes(record));
            verify(http).sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"add-learner", "complete-learner-sync", "promote", "demote", "remove"})
    void requestRecordFailurePreventsAnySubmission(String action, @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
            throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> token = mock(HttpResponse.class);
        when(token.statusCode()).thenReturn(200);
        when(token.body()).thenReturn(
                "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                        .getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(token));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);
        client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                URI.create("http://127.0.0.1:18881/oauth/callback"));
        clearInvocations(http);
        com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.Request request =
                new com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.Request(
                        "https://ops.example.test", "original-operation", 17, "broker-1", "promote", "broker-1:18081", "approved");

        assertThrows(IOException.class, () -> client.submit(directory.resolve("missing/request.json"), request));

        verifyNoInteractions(http);
        java.nio.file.Path legacy = directory.resolve("legacy-confirmation.json");
        String original = """
                {"environment":"https://ops.example.test","operationId":"original-operation",\
                "expectedEpoch":17,"brokerId":"broker-1","action":"%s"}
                """.formatted(action);
        java.nio.file.Files.writeString(legacy, original);
        java.nio.file.Files.setPosixFilePermissions(legacy,
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        IOException failure = assertThrows(IOException.class, () -> client.retry(legacy));
        assertTrue(failure.getMessage().contains("Membership operation requires a membership endpoint"));
        assertEquals(original, java.nio.file.Files.readString(legacy));
        verifyNoInteractions(http);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"invalid\r\nfixture", "invalid fixture", "invalid\tfixture", "非ASCII", "=leading", "embedded=padding"})
    void unsafeBearerTokenCannotEstablishSession(String tokenValue) throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        com.fasterxml.jackson.databind.json.JsonMapper fixtureJson = new com.fasterxml.jackson.databind.json.JsonMapper();
        when(response.body()).thenReturn(fixtureJson.writeValueAsBytes(java.util.Map.of(
                "access_token", tokenValue, "token_type", "Bearer", "expires_in", 300)));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(response));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);

        IOException failure = assertThrows(IOException.class,
                () -> client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                        URI.create("http://127.0.0.1:18881/oauth/callback")));

        assertEquals("Invalid IAM token response", failure.getMessage());
        assertEquals(null, failure.getCause());
        clearInvocations(http);
        assertThrows(IllegalStateException.class, client::queryCluster);
        verifyNoInteractions(http);
    }

    @Test
    void unknownOperationPreservesOriginalIdentityWithoutSubmission() throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> token = mock(HttpResponse.class);
        when(token.statusCode()).thenReturn(200);
        when(token.body()).thenReturn(
                "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                        .getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> operation = mock(HttpResponse.class);
        when(operation.statusCode()).thenReturn(200);
        when(operation.body()).thenReturn(
                "{\"code\":0,\"data\":{\"operationId\":\"original-operation\",\"status\":\"UNKNOWN\"}}"
                        .getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(token), CompletableFuture.completedFuture(operation));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);
        client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                URI.create("http://127.0.0.1:18881/oauth/callback"));

        com.fasterxml.jackson.databind.JsonNode result = assertDoesNotThrow(() -> client.queryOperation("original-operation"));

        assertEquals("original-operation", result.path("operationId").asText());
        assertEquals("UNKNOWN", result.path("status").asText());
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).sendAsync(requests.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        assertEquals("GET", requests.getAllValues().get(1).method());
        assertEquals(URI.create("https://ops.example.test/api/cli/operations/original-operation"),
                requests.getAllValues().get(1).uri());
    }

    @Test
    void overlappingLoginCannotStartAnotherRequest() throws Exception {
        HttpClient http = transport();
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);
        java.util.concurrent.CountDownLatch receiving = new java.util.concurrent.CountDownLatch(1);
        CompletableFuture<HttpResponse<byte[]>> pending = new CompletableFuture<>();
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenAnswer(invocation -> {
                    receiving.countDown();
                    return pending;
                });
        try (java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            java.util.concurrent.Future<?> first = worker.submit(() -> assertThrows(IOException.class,
                    () -> client.exchangeAuthorizationCode(CliLoginSession.start(), "first-code",
                            URI.create("http://127.0.0.1:18881/oauth/callback"))));
            assertTrue(receiving.await(1, TimeUnit.SECONDS));
            try {
                IllegalStateException failure = assertThrows(IllegalStateException.class,
                        () -> client.exchangeAuthorizationCode(CliLoginSession.start(), "second-code",
                                URI.create("http://127.0.0.1:18881/oauth/callback")));
                assertEquals("Another HTTP command is in progress", failure.getMessage());
                verify(http).sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
            } finally {
                pending.completeExceptionally(new IOException("transport-fixture"));
            }
            first.get(1, TimeUnit.SECONDS);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"{}", "null", "{\"code\":1,\"data\":{}}",
            "{\"code\":\"0\",\"data\":{}}", "{\"code\":0}", "{\"code\":0,\"data\":null}",
            "{\"code\":0,\"data\":{}} {}", "{\"code\":0,\"data\":{},\"data\":{}}"})
    void invalidOpsEnvelopeCannotBeReportedAsClusterSuccess(String json) throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> token = mock(HttpResponse.class);
        when(token.statusCode()).thenReturn(200);
        when(token.body()).thenReturn(
                "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                        .getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> cluster = mock(HttpResponse.class);
        when(cluster.statusCode()).thenReturn(200);
        when(cluster.body()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(token), CompletableFuture.completedFuture(cluster));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);
        client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                URI.create("http://127.0.0.1:18881/oauth/callback"));

        IOException failure = assertThrows(IOException.class, client::queryCluster);

        assertEquals("Invalid Ops response", failure.getMessage());
        assertEquals(null, failure.getCause());
    }

    @Test
    void failedReplacementLoginCannotReusePreviousIdentity() throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(
                "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                        .getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(response))
                .thenReturn(CompletableFuture.failedFuture(new IOException("transport-fixture")));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);
        client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                URI.create("http://127.0.0.1:18881/oauth/callback"));

        assertThrows(IOException.class, () -> client.exchangeAuthorizationCode(CliLoginSession.start(),
                "replacement-code", URI.create("http://127.0.0.1:18881/oauth/callback")));
        clearInvocations(http);

        assertThrows(IllegalStateException.class, client::queryCluster);
        verifyNoInteractions(http);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "null", "[]",
            "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300} {}"})
    void malformedTokenDocumentCannotEstablishSession(String json) throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(response));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);

        IOException failure = assertThrows(IOException.class,
                () -> client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                        URI.create("http://127.0.0.1:18881/oauth/callback")));

        assertEquals("Invalid IAM token response", failure.getMessage());
        assertEquals(null, failure.getCause());
        assertThrows(IllegalStateException.class, client::queryCluster);
    }

    @Test
    void duplicateTokenFieldsCannotEstablishSessionOrLeakResponse() throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(("{\"access_token\":\"first-sensitive-fixture\","
                + "\"access_token\":\"second-sensitive-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}")
                .getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(response));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);

        IOException failure = assertThrows(IOException.class,
                () -> client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                        URI.create("http://127.0.0.1:18881/oauth/callback")));

        assertEquals("Invalid IAM token response", failure.getMessage());
        assertEquals(null, failure.getCause());
        assertThrows(IllegalStateException.class, client::queryCluster);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void interruptedWriteCancelsExchangeAndPreservesRecord(boolean retry,
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> token = mock(HttpResponse.class);
        when(token.statusCode()).thenReturn(200);
        when(token.body()).thenReturn(
                "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                        .getBytes(StandardCharsets.UTF_8));
        java.util.concurrent.CountDownLatch waiting = new java.util.concurrent.CountDownLatch(1);
        CompletableFuture<HttpResponse<byte[]>> pending = new CompletableFuture<>() {
            @Override
            public HttpResponse<byte[]> get(long timeout, TimeUnit unit)
                    throws InterruptedException, java.util.concurrent.ExecutionException, TimeoutException {
                waiting.countDown();
                return super.get(timeout, unit);
            }
        };
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> queried = mock(HttpResponse.class);
        when(queried.statusCode()).thenReturn(200);
        when(queried.body()).thenReturn("{\"code\":0,\"data\":{\"epoch\":17}}".getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(token), pending, CompletableFuture.completedFuture(queried));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);
        client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                URI.create("http://127.0.0.1:18881/oauth/callback"));
        java.nio.file.Path record = directory.resolve("original.json");
        com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.Request request =
                new com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.Request(
                        "https://ops.example.test", "original-operation", 17, "broker-1", "promote", "broker-1:18081", null);
        if (retry) {
            com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.write(record, request);
        }
        byte[] original = retry ? java.nio.file.Files.readAllBytes(record) : null;
        java.util.concurrent.atomic.AtomicReference<Throwable> outcome = new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                if (retry) {
                    client.retry(record);
                } else {
                    client.submit(record, request);
                }
            } catch (Throwable failure) {
                outcome.set(failure);
            }
        });
        worker.start();
        try {
            assertTrue(waiting.await(5, TimeUnit.SECONDS));
            worker.interrupt();
            worker.join(5000);
            assertFalse(worker.isAlive());
            org.junit.jupiter.api.Assertions.assertInstanceOf(InterruptedException.class, outcome.get());
            assertTrue(pending.isCancelled());
            assertEquals(request, com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.read(record));
            assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"),
                    java.nio.file.Files.getPosixFilePermissions(record));
            if (retry) {
                org.junit.jupiter.api.Assertions.assertArrayEquals(original, java.nio.file.Files.readAllBytes(record));
            }
            assertEquals(17, client.queryCluster().path("epoch").intValue());
            ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
            verify(http, times(3)).sendAsync(requests.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
            assertEquals(List.of("/oauth2/token", "/api/cli/brokers/broker-1/raft/promote", "/api/cli/cluster-state"),
                    requests.getAllValues().stream().map(value -> value.uri().getPath()).toList());
        } finally {
            worker.interrupt();
            worker.join(5000);
            assertFalse(worker.isAlive());
        }
    }

    @Test
    void interruptedRequestCancelsOriginalExchange() throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        CompletableFuture<HttpResponse<byte[]>> exchange = mock(CompletableFuture.class);
        when(exchange.get(anyLong(), any(TimeUnit.class)))
                .thenThrow(new InterruptedException("interrupted-fixture"));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(exchange);
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);

        assertThrows(InterruptedException.class,
                () -> client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                        URI.create("http://127.0.0.1:18881/oauth/callback")));

        verify(exchange).cancel(true);
    }

    @Test
    void exactlyOneMiBInMultipleBuffersIsAcceptedOnlyAfterCompletion() throws Exception {
        HttpClient http = transport();
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenAnswer(invocation -> {
                    HttpResponse.BodyHandler<byte[]> handler = invocation.getArgument(1);
                    HttpResponse.BodySubscriber<byte[]> body = handler.apply(mock(HttpResponse.ResponseInfo.class));
                    body.onSubscribe(mock(Flow.Subscription.class));
                    byte[] bytes = new byte[1048576];
                    Arrays.fill(bytes, (byte) ' ');
                    byte[] json = "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                            .getBytes(StandardCharsets.UTF_8);
                    System.arraycopy(json, 0, bytes, 0, json.length);
                    body.onNext(List.of(ByteBuffer.wrap(bytes, 0, 524288), ByteBuffer.wrap(bytes, 524288, 524288)));
                    assertFalse(body.getBody().toCompletableFuture().isDone());
                    body.onComplete();
                    return body.getBody().thenApply(payload -> {
                        @SuppressWarnings("unchecked")
                        HttpResponse<byte[]> response = mock(HttpResponse.class);
                        when(response.statusCode()).thenReturn(200);
                        when(response.body()).thenReturn(payload);
                        return response;
                    }).toCompletableFuture();
                });
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);

        assertDoesNotThrow(() -> client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                URI.create("http://127.0.0.1:18881/oauth/callback")));
    }

    @Test
    void oversizedStreamingBodyCancelsBeforeCompletion() throws Exception {
        HttpClient http = transport();
        Flow.Subscription upstream = mock(Flow.Subscription.class);
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenAnswer(invocation -> {
                    HttpResponse.BodyHandler<byte[]> handler = invocation.getArgument(1);
                    HttpResponse.BodySubscriber<byte[]> body = handler.apply(mock(HttpResponse.ResponseInfo.class));
                    body.onSubscribe(upstream);
                    body.onNext(List.of(ByteBuffer.wrap(new byte[1048576])));
                    assertFalse(body.getBody().toCompletableFuture().isDone());
                    body.onNext(List.of(ByteBuffer.wrap(new byte[1])));
                    assertTrue(body.getBody().toCompletableFuture().isCompletedExceptionally());
                    return body.getBody().thenApply(bytes -> {
                        @SuppressWarnings("unchecked")
                        HttpResponse<byte[]> response = mock(HttpResponse.class);
                        when(response.statusCode()).thenReturn(200);
                        when(response.body()).thenReturn(bytes);
                        return response;
                    }).toCompletableFuture();
                });
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);

        assertThrows(IOException.class,
                () -> client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                        URI.create("http://127.0.0.1:18881/oauth/callback")));

        verify(upstream).cancel();
    }

    @Test
    void unfinishedResponseIsCancelledAtTheWholeRequestDeadline() throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        CompletableFuture<HttpResponse<byte[]>> exchange = mock(CompletableFuture.class);
        when(exchange.get(anyLong(), any(TimeUnit.class)))
                .thenThrow(new TimeoutException());
        Flow.Subscription upstream = mock(Flow.Subscription.class);
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenAnswer(invocation -> {
                    HttpResponse.BodyHandler<byte[]> handler = invocation.getArgument(1);
                    HttpResponse.BodySubscriber<byte[]> body = handler.apply(mock(HttpResponse.ResponseInfo.class));
                    body.onSubscribe(upstream);
                    body.onNext(List.of(ByteBuffer.wrap(new byte[]{'{'})));
                    assertFalse(body.getBody().toCompletableFuture().isDone());
                    return exchange;
                });
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);

        assertThrows(HttpTimeoutException.class,
                () -> client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                        URI.create("http://127.0.0.1:18881/oauth/callback")));

        verify(exchange).cancel(true);
        ArgumentCaptor<Long> budget = ArgumentCaptor.forClass(Long.class);
        verify(exchange).get(budget.capture(), eq(TimeUnit.NANOSECONDS));
        assertTrue(budget.getValue() > 0 && budget.getValue() <= 10_000_000_000L);
    }

    @Test
    void cookieEnabledTransportCannotMixBrowserIdentityIntoCli() {
        HttpClient http = transport();
        when(http.cookieHandler()).thenReturn(Optional.of(new CookieManager()));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new OpsCliClient(URI.create("https://iam.example.test"),
                        URI.create("https://ops.example.test"), "ops-cli", http));

        assertEquals("HTTP cookies and authenticators must be disabled", failure.getMessage());
    }

    @Test
    void unboundedConnectionTransportIsRejectedBeforeCredentialsCanBeSent() {
        HttpClient http = transport();
        when(http.connectTimeout()).thenReturn(Optional.empty());

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new OpsCliClient(URI.create("https://iam.example.test"),
                        URI.create("https://ops.example.test"), "ops-cli", http));

        assertEquals("HTTP connection timeout must be positive and at most 10 seconds", failure.getMessage());
    }

    @Test
    void redirectFollowingTransportIsRejectedBeforeCredentialsCanBeSent() {
        HttpClient http = transport();
        when(http.followRedirects()).thenReturn(HttpClient.Redirect.ALWAYS);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new OpsCliClient(URI.create("https://iam.example.test"),
                        URI.create("https://ops.example.test"), "ops-cli", http));

        assertEquals("HTTP redirects must be disabled", failure.getMessage());
    }

    @Test
    void failedRevocationIsReportedWithoutRetainingLocalAuthentication() throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> token = mock(HttpResponse.class);
        when(token.statusCode()).thenReturn(200);
        when(token.body()).thenReturn(                "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                        .getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(token))
                .thenReturn(CompletableFuture.failedFuture(new IOException("transport-fixture")));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);
        client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                URI.create("http://127.0.0.1:18881/oauth/callback"));

        IOException failure = assertThrows(IOException.class, client::logout);

        assertEquals("Remote revocation was not confirmed", failure.getMessage());
        assertThrows(IllegalStateException.class, client::queryCluster);
    }

    @Test
    void logoutRevokesCurrentAuthorizationAndClearsLocalSession() throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> token = mock(HttpResponse.class);
        when(token.statusCode()).thenReturn(200);
        when(token.body()).thenReturn(                "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                        .getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> revoked = mock(HttpResponse.class);
        when(revoked.statusCode()).thenReturn(200);
        when(revoked.body()).thenReturn(new byte[0]);
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(token), CompletableFuture.completedFuture(revoked));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);
        client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                URI.create("http://127.0.0.1:18881/oauth/callback"));

        client.logout();

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).sendAsync(requests.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        assertEquals(URI.create("https://iam.example.test/oauth2/revoke"), requests.getAllValues().get(1).uri());
        assertThrows(IllegalStateException.class, client::queryCluster);
    }

    @Test
    void expiredMemoryTokenCannotSendAnotherRequest() throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(                "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                        .getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(response));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);
        CliLoginSession session = CliLoginSession.start();
        Instant started = Instant.parse("2026-11-02T00:00:00Z");
        Instant expired = started.plusSeconds(300);
        try (MockedStatic<Instant> time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(started);
            client.exchangeAuthorizationCode(session, "code-fixture",
                    URI.create("http://127.0.0.1:18881/oauth/callback"));
            clearInvocations(http);
            time.when(Instant::now).thenReturn(expired);

            assertThrows(IllegalStateException.class, client::queryCluster);

            verifyNoInteractions(http);
        }
    }

    @Test
    void tokenLifetimeBeyondFiveMinutesIsRejected() throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(                "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":301}"
                        .getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(response));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);

        IOException failure = assertThrows(IOException.class,
                () -> client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                        URI.create("http://127.0.0.1:18881/oauth/callback")));

        assertEquals("Invalid IAM token response", failure.getMessage());
        assertThrows(IllegalStateException.class, client::queryCluster);
    }

    @Test
    void unsupportedTokenTypeCannotEstablishSession() throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(                "{\"access_token\":\"opaque-fixture\",\"token_type\":\"MAC\",\"expires_in\":300}"
                        .getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(response));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);

        IOException failure = assertThrows(IOException.class,
                () -> client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                        URI.create("http://127.0.0.1:18881/oauth/callback")));

        assertEquals("Invalid IAM token response", failure.getMessage());
        assertThrows(IllegalStateException.class, client::queryCluster);
    }

    @Test
    void refreshTokenResponseIsRejectedWithoutEstablishingSession() throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(                "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300,\"refresh_token\":\"refresh-fixture\"}"
                        .getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(response));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);

        IOException failure = assertThrows(IOException.class,
                () -> client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                        URI.create("http://127.0.0.1:18881/oauth/callback")));

        assertEquals("Invalid IAM token response", failure.getMessage());
        assertThrows(IllegalStateException.class, client::queryCluster);
    }

    @Test
    void missingAccessTokenCannotEstablishAuthenticatedSession() throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(                "{\"token_type\":\"Bearer\",\"expires_in\":300}".getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(response));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);

        IOException failure = assertThrows(IOException.class,
                () -> client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                        URI.create("http://127.0.0.1:18881/oauth/callback")));

        assertEquals("Invalid IAM token response", failure.getMessage());
        assertThrows(IllegalStateException.class, client::queryCluster);
    }

    @Test
    void successfulExchangeAuthorizesClusterReadWithBearerOnly() throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> token = mock(HttpResponse.class);
        when(token.statusCode()).thenReturn(200);
        when(token.body()).thenReturn(                "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                        .getBytes(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> cluster = mock(HttpResponse.class);
        when(cluster.statusCode()).thenReturn(200);
        when(cluster.body()).thenReturn(                "{\"code\":0,\"data\":{\"epoch\":\"17\"}}".getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(token), CompletableFuture.completedFuture(cluster));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);
        client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                URI.create("http://127.0.0.1:18881/oauth/callback"));

        assertEquals("17", assertDoesNotThrow(client::queryCluster).path("epoch").asText());

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).sendAsync(requests.capture(),
                ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        HttpRequest read = requests.getAllValues().get(1);
        assertEquals(URI.create("https://ops.example.test/api/cli/cluster-state"), read.uri());
        assertEquals("GET", read.method());
        assertEquals("Bearer opaque-fixture", read.headers().firstValue("Authorization").orElseThrow());
        assertEquals(false, read.headers().firstValue("Cookie").isPresent());
    }

    @Test
    void oversizedTokenResponseCannotBeAccepted() throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new byte[1048577]);
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(response));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);

        IOException failure = assertThrows(IOException.class,
                () -> client.exchangeAuthorizationCode(CliLoginSession.start(), "code-fixture",
                        URI.create("http://127.0.0.1:18881/oauth/callback")));

        assertEquals("HTTP response exceeds 1 MiB", failure.getMessage());
    }

    @Test
    void authorizationCodeIsExchangedOnlyAtConfiguredIamEndpoint() throws Exception {
        HttpClient http = transport();
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(                "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                        .getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(response));
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);
        CliLoginSession session = CliLoginSession.start();

        client.exchangeAuthorizationCode(session, "code-fixture", URI.create("http://127.0.0.1:18881/oauth/callback"));

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(request.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        assertEquals(URI.create("https://iam.example.test/oauth2/token"), request.getValue().uri());
        assertEquals("POST", request.getValue().method());
        assertEquals("application/x-www-form-urlencoded",
                request.getValue().headers().firstValue("Content-Type").orElseThrow());
        assertEquals(false, request.getValue().headers().firstValue("Authorization").isPresent());
        HttpResponse.BodySubscriber<byte[]> subscriber = HttpResponse.BodySubscribers.ofByteArray();
        request.getValue().bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(ByteBuffer item) {
                subscriber.onNext(List.of(item));
            }

            @Override
            public void onError(Throwable failure) {
                subscriber.onError(failure);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        });
        String form = new String(subscriber.getBody().toCompletableFuture().get(1, TimeUnit.SECONDS), StandardCharsets.UTF_8);
        assertEquals("client_id=ops-cli&grant_type=authorization_code&code=code-fixture"
                + "&redirect_uri=http%3A%2F%2F127.0.0.1%3A18881%2Foauth%2Fcallback&code_verifier="
                + session.verifier(), form);
    }

    @Test
    void insecureOpsOriginIsRejectedBeforeAnyHttpInteraction() {
        HttpClient http = transport();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new OpsCliClient(URI.create("https://iam.example.test"),
                        URI.create("http://ops.example.test"), "ops-cli", http));

        assertEquals("IAM and Ops must be HTTPS origins", failure.getMessage());
        verifyNoInteractions(http);
    }

    @Test
    void invalidClientConstructionIsRejectedClearly() {
        HttpClient http = transport();

        assertThrows(NullPointerException.class, () -> new OpsCliClient(
                URI.create("https://iam.example.test"), URI.create("https://ops.example.test"), "ops-cli", null));
        assertThrows(IllegalArgumentException.class, () -> new OpsCliClient(
                URI.create("https://iam.example.test"), URI.create("https://ops.example.test"), " ", http));
    }

    @Test
    void unauthenticatedCommandCannotSendAnyHttpRequest() {
        HttpClient http = transport();
        OpsCliClient client = new OpsCliClient(URI.create("https://iam.example.test"),
                URI.create("https://ops.example.test"), "ops-cli", http);

        clearInvocations(http);
        IllegalStateException failure = assertThrows(IllegalStateException.class, client::queryCluster);

        assertEquals("Login is required", failure.getMessage());
        verifyNoInteractions(http);
    }
}
