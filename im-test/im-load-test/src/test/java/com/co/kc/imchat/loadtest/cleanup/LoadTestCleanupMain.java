package com.co.kc.imchat.loadtest.cleanup;

import com.co.kc.imchat.plugin.cache.core.JetCacheFactory;
import com.co.kc.imchat.service.message.infrastructure.notification.redis.RedisImMessageConfirmableStore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

public final class LoadTestCleanupMain {

    private static final Set<String> CHECKPOINTS = Set.of(
            "receipts", "cache-account", "cache-social", "cache-message", "message", "social", "account");

    private LoadTestCleanupMain() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            throw new IllegalStateException("load-test cleanup command failed");
        }
    }

    static int run(String[] args, PrintStream output, PrintStream errors) {
        return run(args, output, errors, () -> new ProductionCleanupRuntime(
                LoadTestCleanupConfig.fromSources(System.getenv(), systemProperties())));
    }

    static int run(String[] args, PrintStream output, PrintStream errors, CleanupRuntimeFactory runtimeFactory) {
        Command command = parseCommand(args, errors);
        if (command == null) {
            return 2;
        }
        try {
            LoadTestBatchManifest manifest = LoadTestBatchManifest.read(command.manifest());
            if (!command.execute()) {
                printDryRun(output, manifest);
                return 0;
            }
            if (!manifest.batchId().equals(command.confirmedBatch())) {
                errors.println("confirmed batch must exactly match manifest batch");
                return 2;
            }
            execute(manifest, command.manifest(), command.journal(), runtimeFactory);
            output.println("cleanup completed");
            return 0;
        } catch (IllegalArgumentException exception) {
            errors.println("manifest or cleanup configuration validation failed");
            printDebug(exception, errors);
            return 2;
        } catch (Exception exception) {
            errors.println("cleanup failed; inspect retained manifest and checkpoints");
            printDebug(exception, errors);
            return 1;
        }
    }

    private static void printDebug(Exception exception, PrintStream errors) {
        if ("1".equals(System.getenv("LOAD_TEST_CLEANUP_DEBUG"))) {
            errors.println(exception);
            for (StackTraceElement frame : exception.getStackTrace()) {
                errors.println("\tat " + frame);
            }
        }
    }

    private static Command parseCommand(String[] args, PrintStream errors) {
        if (args.length == 3 && "dry-run".equals(args[0]) && "--manifest".equals(args[1])) {
            Path path = Path.of(args[2]);
            if (path.isAbsolute()) {
                return new Command(false, path, null, null);
            }
            errors.println("manifest path must be absolute");
            return null;
        }
        if (args.length == 7 && "execute".equals(args[0]) && "--manifest".equals(args[1])
                && "--confirm-batch".equals(args[3]) && "--journal".equals(args[5])) {
            Path path = Path.of(args[2]);
            Path journal = Path.of(args[6]);
            if (path.isAbsolute() && journal.isAbsolute()) {
                return new Command(true, path, args[4], journal);
            }
            errors.println("manifest and journal paths must be absolute");
            return null;
        }
        errors.println("usage: dry-run --manifest <absolute-path> | "
                + "execute --manifest <absolute-path> --confirm-batch <batch> --journal <absolute-path>");
        return null;
    }

    private static void printDryRun(PrintStream output, LoadTestBatchManifest manifest) {
        LoadTestSqlCleaner.CleanupPlan plan = LoadTestSqlCleaner.plan(manifest);
        output.printf("dry-run structural-only users=%d cohorts=%d groups=%d relations=%d privateChats=%d "
                        + "groupChats=%d messages=%d receipts=%d tables=%d%n",
                manifest.users().size(), manifest.cohorts().size(), manifest.groups().size(),
                manifest.relations().size(), manifest.privateChats().size(), manifest.groupChats().size(),
                manifest.messages().size(), manifest.receiptIds().size(), plan.tables().size());
    }

    private static void execute(LoadTestBatchManifest manifest, Path manifestPath, Path journalPath,
            CleanupRuntimeFactory runtimeFactory) throws Exception {
        CheckpointFile checkpointFile = CheckpointFile.open(manifestPath, manifest.batchId());
        Set<String> completed = checkpointFile.completed();
        try (CleanupRuntime runtime = runtimeFactory.create()) {
            LoadTestBatchManifest recovered = runtime.recoverBatch(manifest, journalPath);
            runtime.verifyOwnership(recovered);
            runPhase("receipts", completed, checkpointFile, () -> runtime.cleanReceipts(recovered));
            runPhase("cache-account", completed, checkpointFile, () -> runtime.cleanAccountCache(recovered));
            runPhase("cache-social", completed, checkpointFile, () -> runtime.cleanSocialCache(recovered));
            runPhase("cache-message", completed, checkpointFile, () -> runtime.cleanMessageCache(recovered));
            runtime.cleanSql(recovered, completed, checkpointFile::append);
        }
    }

    private static void runPhase(String phase, Set<String> completed, CheckpointFile checkpointFile,
            CheckedRunnable operation) throws Exception {
        if (completed.contains(phase)) {
            return;
        }
        operation.run();
        checkpointFile.append(phase);
        completed.add(phase);
    }

    private static Map<String, String> systemProperties() {
        Properties properties = System.getProperties();
        Map<String, String> values = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            values.put(name, properties.getProperty(name));
        }
        return values;
    }

    interface CleanupRuntimeFactory {
        CleanupRuntime create() throws Exception;
    }

    interface CleanupRuntime extends AutoCloseable {
        LoadTestBatchManifest recoverBatch(LoadTestBatchManifest seed, Path journal) throws Exception;

        void verifyOwnership(LoadTestBatchManifest manifest) throws Exception;

        void cleanReceipts(LoadTestBatchManifest manifest) throws Exception;

        void cleanAccountCache(LoadTestBatchManifest manifest) throws Exception;

        void cleanSocialCache(LoadTestBatchManifest manifest) throws Exception;

        void cleanMessageCache(LoadTestBatchManifest manifest) throws Exception;

        void cleanSql(LoadTestBatchManifest manifest, Set<String> completed, Consumer<String> checkpoint)
                throws Exception;

        @Override
        void close() throws Exception;
    }

    private static final class ProductionCleanupRuntime implements CleanupRuntime {
        private final LoadTestCleanupConfig config;
        private final HikariDataSource accountDataSource;
        private final HikariDataSource socialDataSource;
        private final HikariDataSource messageDataSource;
        private final LoadTestCacheCleaner cacheCleaner = new LoadTestCacheCleaner();

        private ProductionCleanupRuntime(LoadTestCleanupConfig config) {
            this.config = config;
            this.accountDataSource = dataSource(config.account());
            this.socialDataSource = dataSource(config.social());
            this.messageDataSource = dataSource(config.message());
        }

        @Override
        public void verifyOwnership(LoadTestBatchManifest manifest) throws Exception {
            new LoadTestSqlCleaner(accountDataSource, socialDataSource, messageDataSource, phase -> {
                throw new IllegalStateException("read-only verification must not checkpoint");
            }).verifyOwnership(manifest);
        }

        @Override
        public LoadTestBatchManifest recoverBatch(LoadTestBatchManifest seed, Path journal) throws Exception {
            return new LoadTestBatchRecovery(accountDataSource, socialDataSource, messageDataSource)
                    .recoverOrRead(seed, journal, Path.of(journal + ".cleanup-snapshot.json"));
        }

        @Override
        public void cleanReceipts(LoadTestBatchManifest manifest) {
            try (AnnotationConfigApplicationContext context = MessageRedisCleanupConfiguration.open(config.redis())) {
                RedissonClient redissonClient = context.getBean(RedissonClient.class);
                RedisImMessageConfirmableStore store = new RedisImMessageConfirmableStore(redissonClient);
                try {
                    new LoadTestReceiptCleaner(store).clean(manifest);
                } finally {
                    store.stopConfirming();
                }
            }
        }

        @Override
        public void cleanAccountCache(LoadTestBatchManifest manifest) {
            try (AnnotationConfigApplicationContext context = AccountCacheCleanupConfiguration.open(config.redis())) {
                cacheCleaner.cleanAccount(manifest, context.getBean(JetCacheFactory.class));
            }
        }

        @Override
        public void cleanSocialCache(LoadTestBatchManifest manifest) {
            try (AnnotationConfigApplicationContext context = SocialCacheCleanupConfiguration.open(config.redis())) {
                cacheCleaner.cleanSocial(manifest, context.getBean(JetCacheFactory.class));
            }
        }

        @Override
        public void cleanMessageCache(LoadTestBatchManifest manifest) {
            try (AnnotationConfigApplicationContext context = MessageRedisCleanupConfiguration.open(config.redis())) {
                cacheCleaner.cleanMessage(manifest, context.getBean(RedissonClient.class));
            }
        }

        @Override
        public void cleanSql(LoadTestBatchManifest manifest, Set<String> completed, Consumer<String> checkpoint)
                throws Exception {
            new LoadTestSqlCleaner(accountDataSource, socialDataSource, messageDataSource, checkpoint)
                    .execute(manifest, completed);
        }

        @Override
        public void close() {
            messageDataSource.close();
            socialDataSource.close();
            accountDataSource.close();
        }

        private static HikariDataSource dataSource(LoadTestCleanupConfig.DatabaseTarget target) {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(target.jdbcUrl());
            hikariConfig.setUsername(target.username());
            hikariConfig.setPassword(new String(target.password()));
            hikariConfig.setMinimumIdle(0);
            hikariConfig.setInitializationFailTimeout(-1);
            hikariConfig.setMaximumPoolSize(1);
            return new HikariDataSource(hikariConfig);
        }
    }

    private static final class CheckpointFile {
        private final Path path;
        private final Set<String> completed;
        private final String header;

        private CheckpointFile(Path path, Set<String> completed, String header) {
            this.path = path;
            this.completed = completed;
            this.header = header;
        }

        static CheckpointFile open(Path manifestPath, String batchId) throws IOException {
            Path path = Path.of(manifestPath + ".cleanup-checkpoints");
            String digest;
            try {
                digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(manifestPath)));
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 unavailable", exception);
            }
            String header = "batch=" + batchId + "\nmanifest=" + digest + "\n";
            if (!Files.exists(path)) {
                publish(path, header);
            }
            List<String> lines = Files.readAllLines(path);
            if (lines.size() < 2 || !("batch=" + batchId).equals(lines.getFirst())
                    || !("manifest=" + digest).equals(lines.get(1))) {
                throw new IllegalArgumentException("cleanup checkpoints do not match manifest batch");
            }
            Set<String> completed = new HashSet<>();
            for (int index = 2; index < lines.size(); index++) {
                String checkpoint = lines.get(index);
                if (!CHECKPOINTS.contains(checkpoint)) {
                    throw new IllegalArgumentException("cleanup checkpoints contain an unknown phase");
                }
                completed.add(checkpoint);
            }
            return new CheckpointFile(path, completed, header);
        }

        Set<String> completed() {
            return new HashSet<>(completed);
        }

        void append(String checkpoint) {
            if (!CHECKPOINTS.contains(checkpoint)) {
                throw new IllegalArgumentException("unknown cleanup checkpoint");
            }
            if (completed.add(checkpoint)) {
                try {
                    publish(path, header + String.join("\n", new TreeSet<>(completed)) + "\n");
                } catch (IOException exception) {
                    completed.remove(checkpoint);
                    throw new IllegalStateException("unable to persist cleanup checkpoint", exception);
                }
            }
        }

        private static void publish(Path path, String content) throws IOException {
            Path temporary = Files.createTempFile(path.getParent(), ".cleanup-checkpoints-", ".tmp");
            try {
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    ByteBuffer buffer = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                    channel.force(true);
                }
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                try (FileChannel directory = FileChannel.open(path.getParent(), StandardOpenOption.READ)) {
                    directory.force(true);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private record Command(boolean execute, Path manifest, String confirmedBatch, Path journal) {
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
