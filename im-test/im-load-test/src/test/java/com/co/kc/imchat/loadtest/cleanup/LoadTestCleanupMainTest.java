package com.co.kc.imchat.loadtest.cleanup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadTestCleanupMainTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void dryRunValidatesManifestAndPrintsOnlyProposedCounts() throws Exception {
        Path manifest = temporaryDirectory.resolve("manifest.json");
        Files.writeString(manifest, LoadTestBatchManifestTest.validJson());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();

        int exitCode = LoadTestCleanupMain.run(
                new String[]{"dry-run", "--manifest", manifest.toAbsolutePath().toString()},
                new PrintStream(output), new PrintStream(errors));

        String text = output.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(text.contains("users=2"));
        assertTrue(text.contains("tables=36"));
        assertTrue(text.contains("receipts=2"));
        assertFalse(text.contains("101"));
        assertFalse(text.contains("loadtest-"));
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
    }

    @Test
    void executedModeRequiresExactBatchConfirmationBeforeOpeningRuntime() throws Exception {
        Path manifest = temporaryDirectory.resolve("manifest.json");
        Files.writeString(manifest, LoadTestBatchManifestTest.validJson());
        ByteArrayOutputStream errors = new ByteArrayOutputStream();

        LoadTestCleanupMain.CleanupRuntimeFactory factory = mock(LoadTestCleanupMain.CleanupRuntimeFactory.class);
        int exitCode = LoadTestCleanupMain.run(
                new String[]{"execute", "--manifest", manifest.toAbsolutePath().toString()},
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(errors), factory);

        assertEquals(2, exitCode);
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("--confirm-batch"));
        assertEquals(2, LoadTestCleanupMain.run(
                new String[]{"execute", "--manifest", manifest.toString(), "--confirm-batch", "other"},
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(errors), factory));
        verifyNoInteractions(factory);
    }

    @Test
    void dryRunDoesNotOpenRuntimeEvenWithInvalidExternalConfiguration() throws Exception {
        Path manifest = temporaryDirectory.resolve("manifest.json");
        Files.writeString(manifest, LoadTestBatchManifestTest.validJson());
        LoadTestCleanupMain.CleanupRuntimeFactory factory = mock(LoadTestCleanupMain.CleanupRuntimeFactory.class);
        assertEquals(0, LoadTestCleanupMain.run(new String[]{"dry-run", "--manifest", manifest.toString()},
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()), factory));
        verifyNoInteractions(factory);
        assertFalse(Files.exists(Path.of(manifest + ".cleanup-checkpoints")));
    }

    @Test
    void executeCheckpointsRedisBeforeSqlAndClosesRuntime() throws Exception {
        Path path = temporaryDirectory.resolve("manifest.json");
        Files.writeString(path, LoadTestBatchManifestTest.validJson());
        List<String> events = new ArrayList<>();
        RecordingRuntime runtime = new RecordingRuntime(events, false);
        assertEquals(0, execute(path, runtime));
        assertEquals(List.of("recover", "verify", "receipts", "cache-account", "cache-social", "cache-message", "sql", "close"), events);
        String checkpoints = Files.readString(Path.of(path + ".cleanup-checkpoints"));
        assertTrue(checkpoints.contains("manifest="));
        for (String phase : List.of("receipts", "cache-account", "cache-social", "cache-message", "message", "social", "account")) {
            assertTrue(checkpoints.contains("\n" + phase + "\n"));
        }

        events.clear();
        assertEquals(0, execute(path, new RecordingRuntime(events, false)));
        assertEquals(List.of("recover", "verify", "sql", "close"), events);
    }

    @Test
    void redisFailureStopsBeforeSqlAndResumesOnlyUnfinishedPhases() throws Exception {
        Path path = temporaryDirectory.resolve("manifest.json");
        Files.writeString(path, LoadTestBatchManifestTest.validJson());
        List<String> events = new ArrayList<>();
        assertEquals(1, execute(path, new RecordingRuntime(events, true)));
        assertEquals(List.of("recover", "verify", "receipts", "cache-account", "close"), events);
        assertTrue(Files.readString(Path.of(path + ".cleanup-checkpoints")).endsWith("receipts\n"));
        events.clear();
        assertEquals(0, execute(path, new RecordingRuntime(events, false)));
        assertEquals(List.of("recover", "verify", "cache-account", "cache-social", "cache-message", "sql", "close"), events);
    }

    @Test
    void ownershipFailurePreventsEveryRedisAndSqlMutation() throws Exception {
        Path path = temporaryDirectory.resolve("manifest.json");
        Files.writeString(path, LoadTestBatchManifestTest.validJson());
        LoadTestCleanupMain.CleanupRuntime runtime = mock(LoadTestCleanupMain.CleanupRuntime.class);
        doThrow(new IllegalStateException("unowned user"))
                .when(runtime).verifyOwnership(any());
        when(runtime.recoverBatch(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(1, execute(path, runtime));

        verify(runtime).recoverBatch(any(), any());
        verify(runtime).verifyOwnership(any());
        verify(runtime).close();
        verifyNoMoreInteractions(runtime);
    }

    @Test
    void changedManifestCannotReuseCleanupCheckpoints() throws Exception {
        Path path = temporaryDirectory.resolve("manifest.json");
        Files.writeString(path, LoadTestBatchManifestTest.validJson());
        assertEquals(0, execute(path, new RecordingRuntime(new ArrayList<>(), false)));
        Files.writeString(path, LoadTestBatchManifestTest.validJson() + "\n");
        LoadTestCleanupMain.CleanupRuntime runtime = mock(LoadTestCleanupMain.CleanupRuntime.class);

        assertEquals(2, execute(path, runtime));
        verifyNoInteractions(runtime);
    }

    @Test
    void explicitConfigurationHasNoEndpointOrCredentialFallbacks() {
        assertThrows(IllegalArgumentException.class, () -> LoadTestCleanupConfig.fromSources(Map.of(), Map.of()));
        Map<String, String> values = Map.ofEntries(
                Map.entry("LOAD_TEST_CLEANUP_ACCOUNT_URL", "jdbc:mysql://127.0.0.1:13306/im_chat_account"),
                Map.entry("LOAD_TEST_CLEANUP_ACCOUNT_USER", "u"), Map.entry("LOAD_TEST_CLEANUP_ACCOUNT_PASSWORD", "p"),
                Map.entry("LOAD_TEST_CLEANUP_SOCIAL_URL", "jdbc:mysql://127.0.0.1:13306/im_chat_social"),
                Map.entry("LOAD_TEST_CLEANUP_SOCIAL_USER", "u"), Map.entry("LOAD_TEST_CLEANUP_SOCIAL_PASSWORD", "p"),
                Map.entry("LOAD_TEST_CLEANUP_MESSAGE_URL", "jdbc:mysql://127.0.0.1:13306/im_chat_message"),
                Map.entry("LOAD_TEST_CLEANUP_MESSAGE_USER", "u"), Map.entry("LOAD_TEST_CLEANUP_MESSAGE_PASSWORD", "p"),
                Map.entry("LOAD_TEST_CLEANUP_REDIS_URL", "redis://127.0.0.1:16379"),
                Map.entry("LOAD_TEST_CLEANUP_REDIS_PASSWORD", ""));
        LoadTestCleanupConfig config = LoadTestCleanupConfig.fromSources(values, Map.of());
        assertEquals("redis://127.0.0.1:16379", config.redis().url());
        assertEquals("redis://localhost:26379", LoadTestCleanupConfig.fromSources(values,
                Map.of("loadtest.cleanup.redis.url", "redis://localhost:26379")).redis().url());
    }

    private int execute(Path path, LoadTestCleanupMain.CleanupRuntime runtime) {
        Path journal = Path.of(path + ".journal");
        try {
            if (!Files.exists(journal)) {
                Files.writeString(journal, "mocked-WAL\n");
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        return LoadTestCleanupMain.run(new String[]{"execute", "--manifest", path.toString(),
                        "--confirm-batch", "20260914-t3b1", "--journal", journal.toString()},
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()),
                () -> runtime);
    }

    private static final class RecordingRuntime implements LoadTestCleanupMain.CleanupRuntime {
        private final List<String> events;
        private final boolean failCache;

        private RecordingRuntime(List<String> events, boolean failCache) {
            this.events = events;
            this.failCache = failCache;
        }

        public void verifyOwnership(LoadTestBatchManifest manifest) {
            events.add("verify");
        }

        public LoadTestBatchManifest recoverBatch(LoadTestBatchManifest seed, Path journal) {
            events.add("recover");
            return seed;
        }

        @Override
        public void cleanReceipts(LoadTestBatchManifest manifest) {
            events.add("receipts");
        }

        @Override
        public void cleanAccountCache(LoadTestBatchManifest manifest) {
            events.add("cache-account");
            if (failCache) {
                throw new IllegalStateException("private-secret");
            }
        }

        @Override
        public void cleanSocialCache(LoadTestBatchManifest manifest) {
            events.add("cache-social");
        }

        @Override
        public void cleanMessageCache(LoadTestBatchManifest manifest) {
            events.add("cache-message");
        }

        @Override
        public void cleanSql(LoadTestBatchManifest manifest, Set<String> completed, Consumer<String> checkpoint) {
            events.add("sql");
            for (String owner : List.of("message", "social", "account")) {
                if (!completed.contains(owner)) {
                    checkpoint.accept(owner);
                }
            }
        }

        @Override
        public void close() {
            events.add("close");
        }
    }

    @Test
    void configAcceptsOnlyQueryFreeLoopbackTargetsAndExactSchemasWithoutLeakingCredentials() {
        LoadTestCleanupConfig config = LoadTestCleanupConfig.create(
                "jdbc:mysql://localhost:3306/im_chat_account", "account-user", "account-secret",
                "jdbc:mysql://127.0.0.1:3306/im_chat_social", "social-user", "social-secret",
                "jdbc:mysql://localhost:3306/im_chat_message", "message-user", "message-secret",
                "redis://127.0.0.1:6379", "redis-secret");

        assertEquals("im_chat_account", config.account().schema());
        assertFalse(config.toString().contains("secret"));

        IllegalArgumentException remote = assertThrows(IllegalArgumentException.class, () ->
                LoadTestCleanupConfig.create(
                        "jdbc:mysql://leaked-user:leaked-pass@db.example:3306/im_chat_account",
                        "leaked-user", "leaked-pass",
                        "jdbc:mysql://127.0.0.1:3306/im_chat_social", "u", "p",
                        "jdbc:mysql://127.0.0.1:3306/im_chat_message", "u", "p",
                        "redis://127.0.0.1:6379", "redis-pass"));
        assertFalse(remote.getMessage().contains("leaked-user"));
        assertFalse(remote.getMessage().contains("leaked-pass"));

        assertThrows(IllegalArgumentException.class, () -> LoadTestCleanupConfig.create(
                "jdbc:mysql://localhost:3306/im_chat_account?ssl=false", "u", "p",
                "jdbc:mysql://localhost:3306/im_chat_social", "u", "p",
                "jdbc:mysql://localhost:3306/im_chat_message", "u", "p",
                "redis://localhost:6379", "p"));
        assertThrows(IllegalArgumentException.class, () -> LoadTestCleanupConfig.create(
                "jdbc:mysql://localhost:3306/wrong", "u", "p",
                "jdbc:mysql://localhost:3306/im_chat_social", "u", "p",
                "jdbc:mysql://localhost:3306/im_chat_message", "u", "p",
                "redis://localhost:6379", "p"));
        assertThrows(IllegalArgumentException.class, () -> LoadTestCleanupConfig.create(
                "jdbc:mysql://localhost:3306/im_chat_account", "u", "p",
                "jdbc:mysql://localhost:3306/im_chat_social", "u", "p",
                "jdbc:mysql://localhost:3306/im_chat_message", "u", "p",
                "redis://localhost:6379?db=0", "p"));
    }
}
