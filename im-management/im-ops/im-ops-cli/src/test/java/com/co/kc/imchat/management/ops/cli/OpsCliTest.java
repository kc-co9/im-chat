package com.co.kc.imchat.management.ops.cli;

import com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsCliTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void interruptedWritePreservesOriginalRecoveryContext(boolean retry,
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        int port;
        try (java.net.ServerSocket reservation = new java.net.ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            port = reservation.getLocalPort();
        }
        Path config = directory.resolve("cli.json");
        java.nio.file.Files.writeString(config,
                "{\"iamOrigin\":\"https://iam.example.test\",\"opsOrigin\":\"https://ops.example.test\","
                        + "\"clientId\":\"ops-cli\",\"callbackPort\":" + port + ",\"scope\":\"ops:broker:write\"}");
        java.nio.file.Files.setPosixFilePermissions(config,
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        Path record = directory.resolve("original.json");
        com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.Request request =
                new com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.Request(
                        "https://ops.example.test", "original-operation", 17, "broker-1", "promote", "127.0.0.1:18882", null);
        if (retry) {
            com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.write(record, request);
        }
        byte[] original = retry ? java.nio.file.Files.readAllBytes(record) : null;
        Path observation = directory.resolve("observation.txt");
        Path errors = directory.resolve("stderr.txt");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
                InterruptedOpsCliLauncher.class.getName(), config.toString(), observation.toString())
                .redirectError(errors.toFile()).start();
        try (java.util.concurrent.ExecutorService reader = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            try {
                java.io.BufferedReader output = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                process.getOutputStream().write("login\n".getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().flush();
                String url = reader.submit(output::readLine).get(15, TimeUnit.SECONDS);
                String state = java.util.Arrays.stream(java.net.URI.create(url).getRawQuery().split("&"))
                        .filter(part -> part.startsWith("state=")).findFirst().orElseThrow().substring(6);
                try (java.net.Socket callback = new java.net.Socket("127.0.0.1", port)) {
                    callback.setSoTimeout(5000);
                    callback.getOutputStream().write(("GET /oauth/callback?code=code-fixture&state=" + state
                            + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    callback.getOutputStream().flush();
                    callback.getInputStream().readAllBytes();
                }
                assertEquals("Login completed", reader.submit(output::readLine).get(5, TimeUnit.SECONDS));
                String command = retry ? "operation retry --record " + record + " --confirm\n"
                        : "raft promote --environment https://ops.example.test --broker broker-1"
                                + " --operation-id original-operation --expected-epoch 17 --record " + record
                                + " --membership-endpoint 127.0.0.1:18882 --confirm\n";
                process.getOutputStream().write(command.getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().close();
                assertTrue(process.waitFor(15, TimeUnit.SECONDS));
                String remaining = output.lines().collect(java.util.stream.Collectors.joining("\n"))
                        + "\n" + java.nio.file.Files.readString(errors);
                assertEquals(1, process.exitValue(), remaining);
                assertTrue(remaining.contains("operation show original-operation"), remaining);
                assertTrue(remaining.contains("operation retry --record " + record + " --confirm"), remaining);
                assertTrue(remaining.contains("Submission is unconfirmed"), remaining);
                assertTrue(remaining.contains("Remote revocation was not confirmed"), remaining);
                org.junit.jupiter.api.Assertions.assertFalse(remaining.contains("opaque-fixture"), remaining);
                assertEquals("1,true", java.nio.file.Files.readString(observation));
                assertEquals(request, com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile.read(record));
                if (retry) {
                    org.junit.jupiter.api.Assertions.assertArrayEquals(original, java.nio.file.Files.readAllBytes(record));
                }
            } finally {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void headlessBrowserFallbackPublishesPkceUrlOnlyAfterBinding(
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        int port;
        try (java.net.ServerSocket reservation = new java.net.ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            port = reservation.getLocalPort();
        }
        Path config = directory.resolve("cli.json");
        java.nio.file.Files.writeString(config,
                "{\"iamOrigin\":\"https://iam.example.test\",\"opsOrigin\":\"https://ops.example.test\","
                        + "\"clientId\":\"ops-cli\",\"callbackPort\":" + port + ",\"scope\":\"ops:broker:read\"}");
        java.nio.file.Files.setPosixFilePermissions(config,
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
                OpsCli.class.getName(), "--config", config.toString()).redirectErrorStream(true).start();
        try (java.util.concurrent.ExecutorService reader = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            try {
                process.getOutputStream().write("login\n".getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().flush();
                java.util.concurrent.Future<String> line = reader.submit(() -> new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)).readLine());
                String url = line.get(5, TimeUnit.SECONDS);
                assertTrue(url.startsWith("https://iam.example.test/oauth2/authorize?"), url);
                assertTrue(url.contains("response_type=code"), url);
                assertTrue(url.contains("code_challenge_method=S256"), url);
                assertTrue(url.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A" + port + "%2Foauth%2Fcallback"), url);
                assertTrue(url.contains("state="), url);
                assertTrue(url.contains("code_challenge="), url);
                org.junit.jupiter.api.Assertions.assertFalse(url.contains("code_verifier"), url);
                org.junit.jupiter.api.Assertions.assertThrows(java.net.BindException.class, () -> {
                    try (java.net.ServerSocket probe = new java.net.ServerSocket()) {
                        probe.bind(new java.net.InetSocketAddress("127.0.0.1", port));
                    }
                });
            } finally {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void occupiedCallbackPortFailsBeforeBrowserOrTokenRequest(
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        try (java.net.ServerSocket occupied = new java.net.ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            Path config = directory.resolve("cli.json");
            java.nio.file.Files.writeString(config,
                    "{\"iamOrigin\":\"https://iam.example.test\",\"opsOrigin\":\"https://ops.example.test\","
                            + "\"clientId\":\"ops-cli\",\"callbackPort\":" + occupied.getLocalPort()
                            + ",\"scope\":\"ops:broker:read\"}");
            java.nio.file.Files.setPosixFilePermissions(config,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", System.getProperty("java.class.path"), OpsCli.class.getName(), "--config", config.toString())
                    .redirectErrorStream(true).start();
            try {
                process.getOutputStream().write("login\nexit\n".getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().close();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS));
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(output.contains("Fixed callback port is unavailable"), output);
                org.junit.jupiter.api.Assertions.assertFalse(output.contains("/oauth2/authorize"), output);
            } finally {
                process.destroyForcibly();
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"cluster show", "operation show original-operation"})
    void configuredSessionRejectsUnauthenticatedReadAndAcceptsExit(String command,
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        Path config = directory.resolve("cli.json");
        java.nio.file.Files.writeString(config,
                "{\"iamOrigin\":\"https://iam.example.test\",\"opsOrigin\":\"https://ops.example.test\","
                        + "\"clientId\":\"ops-cli\",\"callbackPort\":18881,\"scope\":\"ops:broker:read\"}");
        java.nio.file.Files.setPosixFilePermissions(config,
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), OpsCli.class.getName(), "--config", config.toString())
                .redirectErrorStream(true).start();
        try {
            process.getOutputStream().write((command + "\nexit\n").getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            assertTrue(process.waitFor(5, TimeUnit.SECONDS), "CLI session did not terminate");
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains("Login is required"), output);
        } finally {
            process.destroyForcibly();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"rwx" + "rwx" + "---", "rwx---rwx"})
    void configurationRejectsWritableAncestorBeforeStartingSession(String permissions,
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        Path unsafe = java.nio.file.Files.createDirectory(directory.resolve("unsafe"));
        java.nio.file.Files.setPosixFilePermissions(unsafe,
                java.nio.file.attribute.PosixFilePermissions.fromString(permissions));
        Path config = unsafe.resolve("cli.json");
        java.nio.file.Files.writeString(config,
                "{\"iamOrigin\":\"https://iam.example.test\",\"opsOrigin\":\"https://ops.example.test\","
                        + "\"clientId\":\"ops-cli\",\"callbackPort\":18881,\"scope\":\"ops:broker:read\"}");
        java.nio.file.Files.setPosixFilePermissions(config,
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), OpsCli.class.getName(), "--config", config.toString())
                .redirectErrorStream(true).start();
        try {
            process.getOutputStream().write("cluster show\nexit\n".getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(1, process.exitValue(), output);
            assertTrue(output.contains("CLI configuration or request failed"), output);
            org.junit.jupiter.api.Assertions.assertFalse(output.contains("Login is required"), output);
        } finally {
            process.destroyForcibly();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,localhost", "true,127.0.0.1"})
    void certificateOrHostnameRejectionPreventsTokenAndBusinessRequests(boolean trustCertificate, String host,
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        try (HttpsCliFixture fixture = new HttpsCliFixture(directory, trustCertificate, host)) {
            assertEquals("CLI configuration or request failed", fixture.loginResult);
            String output = fixture.awaitExit();
            assertEquals(1, fixture.process.exitValue(), output);
            assertEquals(0, fixture.tokenRequests.get());
            assertEquals(0, fixture.submissions.get());
            assertEquals(0, fixture.revocations.get());
            org.junit.jupiter.api.Assertions.assertFalse(output.contains("opaque-fixture"), output);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "promote,127.0.0.1:18882, --membership-endpoint 127.0.0.1:18882",
            "demote,127.0.0.1:18882, --membership-endpoint 127.0.0.1:18882",
            "remove,127.0.0.1:18882, --membership-endpoint 127.0.0.1:18882",
            "complete-learner-sync,127.0.0.1:18882, --membership-endpoint 127.0.0.1:18882",
            "add-learner,127.0.0.1:18882, --membership-endpoint 127.0.0.1:18882"})
    void fixedWritePersistsAndSubmitsExactRequest(String action, String endpoint, String extraOptions,
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        try (HttpsCliFixture fixture = new HttpsCliFixture(directory, true, "localhost")) {
            fixture.assertLoggedIn();
            OperationRequestFile.Request expected = fixture.request(action, endpoint.isEmpty() ? null : endpoint, null);
            fixture.respondToSubmission(action, "APPLIED");
            String output = fixture.finish(fixture.writeCommand(action, extraOptions == null ? "" : extraOptions));
            assertEquals(0, fixture.process.exitValue(), output);
            fixture.assertSubmission(expected, payload(endpoint.isEmpty() ? ""
                    : ",\"membershipEndpoint\":\"127.0.0.1:18882\""));
            assertTrue(output.contains("\"status\":\"APPLIED\""), output);
            assertTrue(output.contains("Environment: " + fixture.origin + "; broker: broker-1; action: " + action), output);
            fixture.assertUnconfirmedRevocation(output);
        }
    }

    @Test
    void confirmationCanSubmitEndpointAndReasonTogether(
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        try (HttpsCliFixture fixture = new HttpsCliFixture(directory, true, "localhost")) {
            fixture.assertLoggedIn();
            fixture.respondToSubmission("complete-learner-sync", "DUPLICATE");
            String output = fixture.finish(fixture.writeCommand("complete-learner-sync",
                    " --membership-endpoint 127.0.0.1:18882 --reason approved-maintenance"));
            assertEquals(0, fixture.process.exitValue(), output);
            fixture.assertSubmission(fixture.request("complete-learner-sync", "127.0.0.1:18882", "approved-maintenance"),
                    payload(",\"membershipEndpoint\":\"127.0.0.1:18882\",\"reason\":\"approved-maintenance\""));
            assertTrue(output.contains("\"status\":\"DUPLICATE\""), output);
        }
    }

    @Test
    void authenticatedWriteDoesNotPersistCredentialsInOwnedConfigurationOrRecord(
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        try (HttpsCliFixture fixture = new HttpsCliFixture(directory, true, "localhost")) {
            fixture.assertLoggedIn();
            Path config = directory.resolve("cli.json");
            byte[] configuration = java.nio.file.Files.readAllBytes(config);
            fixture.respondToSubmission("promote", "APPLIED");
            String output = fixture.finish(fixture.writeCommand("promote", " --membership-endpoint 127.0.0.1:18882"));
            assertEquals(0, fixture.process.exitValue(), output);
            fixture.assertSubmission(fixture.request("promote", "127.0.0.1:18882", null), payload(",\"membershipEndpoint\":\"127.0.0.1:18882\""));
            org.junit.jupiter.api.Assertions.assertArrayEquals(configuration, java.nio.file.Files.readAllBytes(config));
            try (java.util.stream.Stream<Path> files = java.nio.file.Files.list(directory)) {
                assertEquals(java.util.Set.of("fixture.p12", "cli.json", "operation.json"),
                        files.map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
            }
            for (Path document : java.util.List.of(config, fixture.record)) {
                String content = java.nio.file.Files.readString(document);
                org.junit.jupiter.api.Assertions.assertFalse(content.contains("opaque-fixture"));
                org.junit.jupiter.api.Assertions.assertFalse(content.contains("code-fixture"));
                org.junit.jupiter.api.Assertions.assertFalse(content.contains("access_token"));
                org.junit.jupiter.api.Assertions.assertFalse(content.contains("refresh_token"));
            }
            fixture.assertUnconfirmedRevocation(output);
        }
    }

    @Test
    void writeReasonIsPreservedInRecordAndSubmission(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        try (HttpsCliFixture fixture = new HttpsCliFixture(directory, true, "localhost")) {
            fixture.assertLoggedIn();
            fixture.respondToSubmission("promote", "APPLIED");
            String output = fixture.finish(fixture.writeCommand("promote", " --membership-endpoint 127.0.0.1:18882 --reason approved-maintenance"));
            assertEquals(0, fixture.process.exitValue(), output);
            fixture.assertSubmission(fixture.request("promote", "127.0.0.1:18882", "approved-maintenance"),
                    payload(",\"membershipEndpoint\":\"127.0.0.1:18882\",\"reason\":\"approved-maintenance\""));
            assertTrue(output.contains("\"status\":\"APPLIED\""), output);
            assertTrue(output.contains("Environment: " + fixture.origin + "; broker: broker-1; action: promote"), output);
            fixture.assertUnconfirmedRevocation(output);
        }
    }

    @Test
    void explicitRetrySubmitsOriginalRecordWithoutRewritingBytes(
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        try (HttpsCliFixture fixture = new HttpsCliFixture(directory, true, "localhost")) {
            fixture.assertLoggedIn();
            OperationRequestFile.Request expected = fixture.request("promote", "127.0.0.1:18882", null);
            OperationRequestFile.write(fixture.record, expected);
            byte[] original = java.nio.file.Files.readAllBytes(fixture.record);
            fixture.respondToSubmission("promote", "APPLIED");
            String output = fixture.finish("operation retry --record " + fixture.record + " --confirm\nexit\n");
            assertEquals(0, fixture.process.exitValue(), output);
            fixture.assertSubmission(expected, payload(",\"membershipEndpoint\":\"127.0.0.1:18882\""));
            org.junit.jupiter.api.Assertions.assertArrayEquals(original, java.nio.file.Files.readAllBytes(fixture.record));
            assertTrue(output.contains("\"status\":\"APPLIED\""), output);
            fixture.assertUnconfirmedRevocation(output);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "REJECTED,1", "CONFLICT,1", "NOT_LEADER,1", "UNAVAILABLE,1", "IN_PROGRESS,1", "DUPLICATE,0"})
    void submissionStatusDeterminesExitClassification(String status, int exitCode,
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        try (HttpsCliFixture fixture = new HttpsCliFixture(directory, true, "localhost")) {
            fixture.assertLoggedIn();
            fixture.respondToSubmission("promote", status);
            String output = fixture.finish(fixture.writeCommand("promote", " --membership-endpoint 127.0.0.1:18882"));
            assertEquals(exitCode, fixture.process.exitValue(), output);
            fixture.assertSubmission(fixture.request("promote", "127.0.0.1:18882", null), payload(",\"membershipEndpoint\":\"127.0.0.1:18882\""));
            assertTrue(output.contains("\"status\":\"" + status + "\""), output);
            if (status.equals("IN_PROGRESS")) {
                assertTrue(output.contains("registration is unconfirmed"), output);
                assertTrue(output.contains("may not find it before registration"), output);
                assertTrue(output.contains("do not create a new operationId"), output);
                assertTrue(java.nio.file.Files.exists(fixture.record));
            }
            assertTrue(output.contains("Environment: " + fixture.origin + "; broker: broker-1; action: promote"), output);
            fixture.assertUnconfirmedRevocation(output);
        }
    }

    @Test
    void disconnectedSubmissionReportsUnknownWithOriginalRecoveryGuidance(
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        try (HttpsCliFixture fixture = new HttpsCliFixture(directory, true, "localhost")) {
            fixture.assertLoggedIn();
            fixture.respondToSubmission("promote", null);
            String output = fixture.finish(fixture.writeCommand("promote", " --membership-endpoint 127.0.0.1:18882"));
            assertEquals(1, fixture.process.exitValue(), output);
            fixture.assertSubmission(fixture.request("promote", "127.0.0.1:18882", null), payload(",\"membershipEndpoint\":\"127.0.0.1:18882\""));
            assertTrue(output.contains("\"status\":\"UNKNOWN\""), output);
            assertTrue(output.contains("operation show original-operation"), output);
            assertTrue(output.contains("operation retry --record " + fixture.record), output);
            assertTrue(java.nio.file.Files.isRegularFile(fixture.record));
            assertTrue(output.contains("Environment: " + fixture.origin + "; broker: broker-1; action: promote"), output);
            fixture.assertUnconfirmedRevocation(output);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "false,false,17", "true,true,17", "true,false,-1"})
    void authenticatedInvalidWriteDoesNotCreateRecordOrSend(boolean confirmed, boolean wrongEnvironment, long epoch,
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        try (HttpsCliFixture fixture = new HttpsCliFixture(directory, true, "localhost")) {
            fixture.assertLoggedIn();
            fixture.respondToSubmission("promote", "APPLIED");
            String command = "raft promote --environment "
                    + (wrongEnvironment ? "https://other.example.test" : fixture.origin)
                    + " --broker broker-1 --operation-id original-operation --expected-epoch " + epoch
                    + " --record " + fixture.record + " --membership-endpoint 127.0.0.1:18882" + (confirmed ? " --confirm" : "") + "\nexit\n";
            String output = fixture.finish(command);
            assertEquals(1, fixture.process.exitValue(), output);
            assertEquals(0, fixture.submissions.get());
            org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(fixture.record));
            fixture.assertUnconfirmedRevocation(output);
        }
    }

    @Test
    void authenticatedWriteRejectsExistingRecordWithoutSendingOrChangingBytes(
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        try (HttpsCliFixture fixture = new HttpsCliFixture(directory, true, "localhost")) {
            fixture.assertLoggedIn();
            OperationRequestFile.write(fixture.record, fixture.request("promote", "127.0.0.1:18882", null));
            byte[] original = java.nio.file.Files.readAllBytes(fixture.record);
            fixture.respondToSubmission("promote", "APPLIED");
            String output = fixture.finish(fixture.writeCommand("promote", " --membership-endpoint 127.0.0.1:18882"));
            assertEquals(1, fixture.process.exitValue(), output);
            assertEquals(0, fixture.submissions.get());
            org.junit.jupiter.api.Assertions.assertArrayEquals(original, java.nio.file.Files.readAllBytes(fixture.record));
            fixture.assertUnconfirmedRevocation(output);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"logout\nexit\n", "exit\n"})
    void logoutOrExitReportsUnconfirmedRevocationWithoutFailingSession(String commands,
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        try (HttpsCliFixture fixture = new HttpsCliFixture(directory, true, "localhost")) {
            fixture.assertLoggedIn();
            String output = fixture.finish(commands);
            assertEquals(0, fixture.process.exitValue(), output);
            assertEquals(0, fixture.submissions.get());
            fixture.assertUnconfirmedRevocation(output);
        }
    }

    @Test
    void readFailureRetainsNonzeroExitWhenRevocationAlsoFails(
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        try (HttpsCliFixture fixture = new HttpsCliFixture(directory, true, "localhost")) {
            fixture.assertLoggedIn();
            String output = fixture.finish("cluster show\nexit\n");
            assertEquals(1, fixture.process.exitValue(), output);
            assertTrue(output.contains("CLI configuration or request failed"), output);
            assertEquals(0, fixture.submissions.get());
            fixture.assertUnconfirmedRevocation(output);
        }
    }

    private static String payload(String extraFields) {
        return "{\"operationId\":\"original-operation\",\"expectedEpoch\":17" + extraFields + "}";
    }

    private static final class HttpsCliFixture implements AutoCloseable {
        private final com.sun.net.httpserver.HttpsServer server;
        private final java.util.concurrent.ExecutorService reader = java.util.concurrent.Executors.newSingleThreadExecutor();
        private final java.util.concurrent.atomic.AtomicInteger revocations = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicInteger tokenRequests = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicInteger submissions = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicReference<Throwable> submissionFailure = new java.util.concurrent.atomic.AtomicReference<>();
        private final java.util.concurrent.atomic.AtomicReference<OperationRequestFile.Request> savedAtSubmission = new java.util.concurrent.atomic.AtomicReference<>();
        private final java.util.concurrent.atomic.AtomicReference<com.fasterxml.jackson.databind.JsonNode> submittedPayload = new java.util.concurrent.atomic.AtomicReference<>();
        private final java.util.concurrent.atomic.AtomicReference<String> authorization = new java.util.concurrent.atomic.AtomicReference<>();
        private final java.util.concurrent.atomic.AtomicReference<String> method = new java.util.concurrent.atomic.AtomicReference<>();
        private final Path record;
        private final String origin;
        private Process process;
        private java.io.BufferedReader output;
        private String loginResult;

        private HttpsCliFixture(Path directory, boolean trustCertificate, String host) throws Exception {
            String password = java.util.UUID.randomUUID().toString();
            Path store = directory.resolve("fixture.p12");
            Process keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                    "-genkeypair", "-alias", "localhost", "-keyalg", "RSA", "-storetype", "PKCS12",
                    "-keystore", store.toString(), "-storepass", password, "-keypass", password,
                    "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-validity", "1", "-noprompt")
                    .redirectErrorStream(true).start();
            try {
                assertTrue(keytool.waitFor(15, TimeUnit.SECONDS));
                assertEquals(0, keytool.exitValue(), new String(keytool.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            } finally {
                keytool.destroyForcibly();
            }
            java.security.KeyStore keys = java.security.KeyStore.getInstance("PKCS12");
            try (java.io.InputStream input = java.nio.file.Files.newInputStream(store)) {
                keys.load(input, password.toCharArray());
            }
            javax.net.ssl.KeyManagerFactory managers = javax.net.ssl.KeyManagerFactory.getInstance(
                    javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
            managers.init(keys, password.toCharArray());
            javax.net.ssl.SSLContext tls = javax.net.ssl.SSLContext.getInstance("TLS");
            tls.init(managers.getKeyManagers(), null, null);
            server = com.sun.net.httpserver.HttpsServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            server.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(tls));
            record = directory.resolve("operation.json");
            origin = "https://" + host + ":" + server.getAddress().getPort();
            try {
                server.createContext("/oauth2/token", exchange -> {
                    tokenRequests.incrementAndGet();
                    exchange.getRequestBody().readAllBytes();
                    byte[] body = "{\"access_token\":\"opaque-fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    try (java.io.OutputStream response = exchange.getResponseBody()) {
                        response.write(body);
                    }
                });
                server.createContext("/oauth2/revoke", exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    revocations.incrementAndGet();
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                });
                server.createContext("/api/cli/brokers/", exchange -> {
                    submissions.incrementAndGet();
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                });
                server.start();
                int port;
                try (java.net.ServerSocket reservation = new java.net.ServerSocket(0, 1,
                        java.net.InetAddress.getByName("127.0.0.1"))) {
                    port = reservation.getLocalPort();
                }
                Path config = directory.resolve("cli.json");
                java.nio.file.Files.writeString(config, "{\"iamOrigin\":\"" + origin + "\",\"opsOrigin\":\"" + origin
                        + "\",\"clientId\":\"ops-cli\",\"callbackPort\":" + port + ",\"scope\":\"ops:broker:read\"}");
                java.nio.file.Files.setPosixFilePermissions(config,
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
                process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-Djava.awt.headless=true", "-Djavax.net.ssl.trustStore="
                                + (trustCertificate ? store : Path.of(System.getProperty("java.home"), "lib", "security", "cacerts")),
                        "-Djavax.net.ssl.trustStorePassword=" + (trustCertificate ? password
                                : "changeit"),
                        "-cp", System.getProperty("java.class.path"), OpsCli.class.getName(), "--config", config.toString())
                        .redirectErrorStream(true).start();
                output = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                process.getOutputStream().write("login\n".getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().flush();
                String url = reader.submit(output::readLine).get(5, TimeUnit.SECONDS);
                String state = java.util.Arrays.stream(java.net.URI.create(url).getRawQuery().split("&"))
                        .filter(part -> part.startsWith("state=")).findFirst().orElseThrow().substring(6);
                try (java.net.Socket callback = new java.net.Socket("127.0.0.1", port)) {
                    callback.setSoTimeout(5000);
                    callback.getOutputStream().write(("GET /oauth/callback?code=code-fixture&state=" + state
                            + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    callback.getOutputStream().flush();
                    callback.getInputStream().readAllBytes();
                }
                loginResult = reader.submit(output::readLine).get(10, TimeUnit.SECONDS);
            } catch (Exception | AssertionError failure) {
                close();
                throw failure;
            }
        }

        private void assertLoggedIn() {
            assertEquals("Login completed", loginResult);
            assertEquals(1, tokenRequests.get());
        }

        private OperationRequestFile.Request request(String action, String endpoint, String reason) {
            return new OperationRequestFile.Request(origin, "original-operation", 17, "broker-1", action, endpoint, reason);
        }

        private String writeCommand(String action, String extraOptions) {
            String options = extraOptions.strip();
            return "raft " + action + " --environment " + origin + " --broker broker-1 --operation-id original-operation"
                    + " --expected-epoch 17 --record " + record
                    + (options.isEmpty() ? "" : " " + options) + " --confirm\nexit\n";
        }

        private void respondToSubmission(String action, String status) {
            server.createContext("/api/cli/brokers/broker-1/raft/" + action, exchange -> {
                submissions.incrementAndGet();
                try {
                    savedAtSubmission.set(OperationRequestFile.read(record));
                    authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    method.set(exchange.getRequestMethod());
                    submittedPayload.set(new com.fasterxml.jackson.databind.ObjectMapper().readTree(exchange.getRequestBody()));
                    if (status == null) {
                        exchange.close();
                        return;
                    }
                    byte[] body = ("{\"code\":0,\"data\":{\"operationId\":\"original-operation\",\"status\":\""
                            + status + "\"}}").getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    try (java.io.OutputStream response = exchange.getResponseBody()) {
                        response.write(body);
                    }
                } catch (Throwable failure) {
                    submissionFailure.set(failure);
                    exchange.close();
                }
            });
        }

        private String finish(String commands) throws Exception {
            process.getOutputStream().write(commands.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            return awaitExit();
        }

        private String awaitExit() throws Exception {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS));
            return output.lines().collect(java.util.stream.Collectors.joining("\n"));
        }

        private void assertSubmission(OperationRequestFile.Request expected, String expectedPayload) throws Exception {
            assertEquals(null, submissionFailure.get());
            assertEquals(1, submissions.get());
            assertEquals(expected, savedAtSubmission.get());
            assertEquals(expected, OperationRequestFile.read(record));
            assertEquals("Bearer opaque-fixture", authorization.get());
            assertEquals("POST", method.get());
            assertEquals(new com.fasterxml.jackson.databind.ObjectMapper().readTree(expectedPayload), submittedPayload.get());
            assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"),
                    java.nio.file.Files.getPosixFilePermissions(record));
        }

        private void assertUnconfirmedRevocation(String remaining) {
            assertTrue(remaining.contains("Remote revocation was not confirmed"), remaining);
            org.junit.jupiter.api.Assertions.assertFalse(remaining.contains("opaque-fixture"), remaining);
            assertEquals(1, revocations.get());
        }

        @Override
        public void close() throws Exception {
            try {
                if (process != null) {
                    process.destroyForcibly();
                    assertTrue(process.waitFor(5, TimeUnit.SECONDS));
                }
            } finally {
                server.stop(0);
                reader.close();
            }
        }
    }


    @Test
    void unauthenticatedPromoteFailsWithoutCreatingRequestRecord(
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        Path config = directory.resolve("cli.json");
        Path record = directory.resolve("operation.json");
        java.nio.file.Files.writeString(config,
                "{\"iamOrigin\":\"https://iam.example.test\",\"opsOrigin\":\"https://ops.example.test\","
                        + "\"clientId\":\"ops-cli\",\"callbackPort\":18881,\"scope\":\"ops:broker:write\"}");
        java.nio.file.Files.setPosixFilePermissions(config,
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), OpsCli.class.getName(), "--config", config.toString())
                .redirectErrorStream(true).start();
        try {
            String command = "raft promote --environment https://ops.example.test --broker broker-1"
                    + " --operation-id original-operation --expected-epoch 17 --record " + record
                    + " --membership-endpoint 127.0.0.1:18882 --confirm\nexit\n";
            process.getOutputStream().write(command.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(1, process.exitValue(), output);
            assertTrue(output.contains("Login is required"), output);
            org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(record));
        } finally {
            process.destroyForcibly();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"raft promote", "operation retry", "raft destroy", "raft promote --confirm",
            "raft complete-learner-sync --environment https://ops.example.test --broker broker-1 --operation-id original-operation --expected-epoch 17 --record missing.json --confirm",
            "raft complete-learner-sync --environment https://ops.example.test --broker broker-1 --operation-id original-operation --expected-epoch 17 --record missing.json --reason maintenance --membership-endpoint broker-1:18081 --confirm"})
    void malformedWriteCommandExitsNonzero(String command,
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        Path config = directory.resolve("cli.json");
        java.nio.file.Files.writeString(config,
                "{\"iamOrigin\":\"https://iam.example.test\",\"opsOrigin\":\"https://ops.example.test\","
                        + "\"clientId\":\"ops-cli\",\"callbackPort\":18881,\"scope\":\"ops:broker:write\"}");
        java.nio.file.Files.setPosixFilePermissions(config,
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), OpsCli.class.getName(), "--config", config.toString())
                .redirectErrorStream(true).start();
        try {
            process.getOutputStream().write((command + "\nexit\n").getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(1, process.exitValue(), output);
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void helpExitsSuccessfullyWithoutConfiguration() throws Exception {
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), OpsCli.class.getName(), "--help")
                .redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(5, TimeUnit.SECONDS), "CLI help did not terminate");
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains("--config"), output);
            assertTrue(output.contains("cluster show"), output);
            assertTrue(output.contains("--operation-id"), output);
            assertTrue(output.contains("--expected-epoch"), output);
            assertTrue(output.contains("operation retry --record"), output);
            assertTrue(output.contains("Whitespace-separated"), output);
            assertTrue(output.contains("not operation completion"), output);
        } finally {
            process.destroyForcibly();
        }
    }
}
