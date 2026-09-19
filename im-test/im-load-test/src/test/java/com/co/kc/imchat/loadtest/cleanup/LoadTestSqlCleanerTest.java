package com.co.kc.imchat.loadtest.cleanup;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadTestSqlCleanerTest {

    private final List<Connection> openConnections = new ArrayList<>();

    @AfterEach
    void clearInterruptAndCloseConnections() throws Exception {
        Thread.interrupted();
        for (Connection connection : openConnections) {
            connection.close();
        }
    }

    @Test
    void dryRunBuildsTheExplicitPlanWithoutOpeningADataSource() {
        DataSource forbidden = forbiddenDataSource();
        LoadTestSqlCleaner cleaner = new LoadTestSqlCleaner(forbidden, forbidden, forbidden, ignored -> { });

        LoadTestSqlCleaner.CleanupPlan plan = cleaner.dryRun(manifest());

        assertEquals(36, plan.tables().size());
        assertEquals("db_im_private_inbox_message_0", plan.tables().getFirst());
        assertEquals("db_user", plan.tables().getLast());
        assertEquals(2, plan.userCount());
    }

    @Test
    void queriesActualOwnershipThenDeletesInAllowlistedOrderWithBoundParameters() throws Exception {
        DatabaseFixture fixture = fixture();
        List<String> checkpoints = new ArrayList<>();
        LoadTestSqlCleaner cleaner = new LoadTestSqlCleaner(
                fixture.account(), fixture.social(), fixture.message(), checkpoints::add);

        cleaner.execute(manifest());

        assertEquals(List.of("message", "social", "account"), checkpoints);
        List<SqlCall> deletes = fixture.calls().stream()
                .filter(call -> call.sql().startsWith("DELETE"))
                .toList();
        assertEquals(36, deletes.size());
        assertTrue(deletes.getFirst().sql().contains("db_im_private_inbox_message_0"));
        assertTrue(deletes.get(16).sql().contains("db_im_private_chat_0"));
        assertTrue(deletes.get(32).sql().contains("db_friend"));
        assertTrue(deletes.get(33).sql().contains("db_im_group_member"));
        assertTrue(deletes.get(34).sql().contains("db_im_group"));
        assertTrue(deletes.getLast().sql().contains("db_user"));
        assertTrue(deletes.stream().allMatch(call -> call.sql().contains(" WHERE ")));
        assertTrue(deletes.stream().allMatch(call -> !call.parameters().isEmpty()));
        assertTrue(deletes.stream().noneMatch(call -> call.sql().contains("101")
                || call.sql().contains("loadtest-20260914-t3b1")));
        assertEquals(1, count(fixture.account(), "SELECT COUNT(*) FROM db_user WHERE user_id=900"));
        assertEquals(1, count(fixture.social(), "SELECT COUNT(*) FROM db_friend WHERE user_id=900"));
        assertEquals(1, count(fixture.message(),
                "SELECT COUNT(*) FROM db_im_private_inbox_message_0 WHERE user_id=900"));
        assertEquals(0, count(fixture.account(), "SELECT COUNT(*) FROM db_user WHERE user_id=101"));
    }

    @Test
    void interruptionRollsBackTheCurrentSchemaAndDoesNotWriteItsCheckpoint() throws Exception {
        DatabaseFixture fixture = fixture();
        DataSource interruptingMessage = interruptAfterFirstDelete(fixture.message(), fixture.calls());
        List<String> checkpoints = new ArrayList<>();
        LoadTestSqlCleaner cleaner = new LoadTestSqlCleaner(
                fixture.account(), fixture.social(), interruptingMessage, checkpoints::add);

        assertThrows(InterruptedException.class, () -> cleaner.execute(manifest()));

        assertTrue(checkpoints.isEmpty());
        assertEquals(1, count(fixture.message(),
                "SELECT COUNT(*) FROM db_im_private_inbox_message_0 WHERE user_id=101"));
        assertEquals(1, count(fixture.social(), "SELECT COUNT(*) FROM db_friend WHERE user_id=101"));
        assertEquals(1, count(fixture.account(), "SELECT COUNT(*) FROM db_user WHERE user_id=101"));
    }

    @ParameterizedTest(name = "{0} commit without checkpoint recovers")
    @ValueSource(strings = {"message", "social", "account"})
    void commitWithoutCheckpointIsRecoveredFromAllAbsentState(String owner) throws Exception {
        DatabaseFixture fixture = fixture();
        IdempotentCrashingCheckpoint checkpoints = new IdempotentCrashingCheckpoint(owner);
        LoadTestSqlCleaner cleaner = new LoadTestSqlCleaner(
                fixture.account(), fixture.social(), fixture.message(), checkpoints::record);

        assertThrows(IllegalStateException.class, () -> cleaner.execute(manifest()));
        assertEquals(0, targetCount(fixture, owner));
        assertFalse(checkpoints.persisted().contains(owner));

        LoadTestSqlCleaner resumed = new LoadTestSqlCleaner(
                fixture.account(), fixture.social(), fixture.message(), checkpoints::record);
        resumed.execute(manifest());
        resumed.execute(manifest());

        assertEquals(Set.of("message", "social", "account"), checkpoints.persisted());
        assertEquals(0, targetCount(fixture, "message"));
        assertEquals(0, targetCount(fixture, "social"));
        assertEquals(0, targetCount(fixture, "account"));
        assertEquals(1, count(fixture.account(), "SELECT COUNT(*) FROM db_user WHERE user_id=900"));
    }

    @ParameterizedTest(name = "{0} partial deletion fails closed")
    @ValueSource(strings = {"message", "social", "account"})
    void partialTargetDeletionFailsClosed(String owner) throws Exception {
        DatabaseFixture fixture = fixture();
        removeOneTarget(fixture, owner);
        List<String> checkpoints = new ArrayList<>();
        LoadTestSqlCleaner cleaner = new LoadTestSqlCleaner(
                fixture.account(), fixture.social(), fixture.message(), checkpoints::add);

        assertThrows(IllegalStateException.class, () -> cleaner.execute(manifest()));
        assertFalse(checkpoints.contains(owner));
    }

    @ParameterizedTest(name = "{0} ownership mismatch fails closed")
    @ValueSource(strings = {"message", "social", "account"})
    void ownershipMismatchFailsClosed(String owner) throws Exception {
        DatabaseFixture fixture = fixture();
        forgeOwnership(fixture, owner);
        List<String> checkpoints = new ArrayList<>();
        LoadTestSqlCleaner cleaner = new LoadTestSqlCleaner(
                fixture.account(), fixture.social(), fixture.message(), checkpoints::add);

        assertThrows(IllegalStateException.class, () -> cleaner.execute(manifest()));
        assertFalse(checkpoints.contains(owner));
    }

    @ParameterizedTest(name = "{0} datasource identity mismatch fails before transaction")
    @ValueSource(strings = {"message", "social", "account"})
    void miswiredDataSourceFailsBeforeTransactionMutationOrCheckpoint(String owner) throws Exception {
        DatabaseFixture fixture = fixture();
        List<String> identityEvents = new ArrayList<>();
        DataSource invalid = invalidIdentity(dataSource(fixture, owner), owner, identityEvents);
        List<String> checkpoints = new ArrayList<>();
        LoadTestSqlCleaner cleaner = cleanerWith(fixture, owner, invalid, checkpoints);
        int before = targetCount(fixture, owner);
        fixture.calls().clear();

        assertThrows(IllegalStateException.class,
                () -> cleaner.execute(manifest(), priorCheckpoints(owner)));

        assertEquals(before, targetCount(fixture, owner));
        assertTrue(fixture.calls().isEmpty());
        assertFalse(identityEvents.contains("setAutoCommit"));
        assertTrue(checkpoints.isEmpty());
    }

    @ParameterizedTest(name = "{0} nonlocal or wrong-database metadata URL fails before transaction")
    @ValueSource(strings = {"message", "social", "account"})
    void unsafeMetadataUrlFailsBeforeTransactionMutationOrCheckpoint(String owner) throws Exception {
        DatabaseFixture fixture = fixture();
        List<String> identityEvents = new ArrayList<>();
        DataSource invalid = identityOverride(dataSource(fixture, owner), expectedSchema(owner),
                "jdbc:mysql://db.example:3306/wrong_schema", false, identityEvents);
        List<String> checkpoints = new ArrayList<>();
        LoadTestSqlCleaner cleaner = cleanerWith(fixture, owner, invalid, checkpoints);
        int before = targetCount(fixture, owner);
        fixture.calls().clear();

        assertThrows(IllegalStateException.class,
                () -> cleaner.execute(manifest(), priorCheckpoints(owner)));

        assertEquals(before, targetCount(fixture, owner));
        assertTrue(fixture.calls().isEmpty());
        assertFalse(identityEvents.contains("setAutoCommit"));
        assertTrue(checkpoints.isEmpty());
    }

    private LoadTestBatchManifest manifest() {
        return LoadTestBatchManifest.parse(LoadTestBatchManifestTest.validJson());
    }

    private DatabaseFixture fixture() throws Exception {
        List<SqlCall> calls = new ArrayList<>();
        DataSource account = identifiedRecording(database("account"), calls, "im_chat_account");
        DataSource social = identifiedRecording(database("social"), calls, "im_chat_social");
        DataSource message = identifiedRecording(database("message"), calls, "im_chat_message");
        createAccount(account);
        createSocial(social);
        createMessage(message);
        calls.clear();
        return new DatabaseFixture(account, social, message, calls);
    }

    private static DataSource dataSource(DatabaseFixture fixture, String owner) {
        return switch (owner) {
            case "message" -> fixture.message();
            case "social" -> fixture.social();
            case "account" -> fixture.account();
            default -> throw new IllegalArgumentException("unknown owner");
        };
    }

    private static LoadTestSqlCleaner cleanerWith(DatabaseFixture fixture, String owner, DataSource replacement,
            List<String> checkpoints) {
        return new LoadTestSqlCleaner(
                owner.equals("account") ? replacement : fixture.account(),
                owner.equals("social") ? replacement : fixture.social(),
                owner.equals("message") ? replacement : fixture.message(),
                checkpoints::add);
    }

    private static Set<String> priorCheckpoints(String owner) {
        return switch (owner) {
            case "message" -> Set.of();
            case "social" -> Set.of("message");
            case "account" -> Set.of("message", "social");
            default -> throw new IllegalArgumentException("unknown owner");
        };
    }

    private static String expectedSchema(String owner) {
        return "im_chat_" + owner;
    }

    private static DataSource invalidIdentity(DataSource delegate, String owner, List<String> events) {
        return switch (owner) {
            case "message" -> identityOverride(delegate, "im_chat_social",
                    "jdbc:mysql://127.0.0.1:3306/im_chat_message", false, events);
            case "social" -> identityOverride(delegate, "im_chat_social", null, false, events);
            case "account" -> identityOverride(delegate, "im_chat_account", null, true, events);
            default -> throw new IllegalArgumentException("unknown owner");
        };
    }

    private static void removeOneTarget(DatabaseFixture fixture, String owner) throws SQLException {
        switch (owner) {
            case "message" -> execute(fixture.message(),
                    "DELETE FROM db_im_private_inbox_message_0 WHERE chat_id=301");
            case "social" -> execute(fixture.social(),
                    "DELETE FROM db_friend WHERE user_id=101 AND friend_user_id=102");
            case "account" -> execute(fixture.account(), "DELETE FROM db_user WHERE user_id=101");
            default -> throw new IllegalArgumentException("unknown owner");
        }
    }

    private static void forgeOwnership(DatabaseFixture fixture, String owner) throws SQLException {
        switch (owner) {
            case "message" -> execute(fixture.message(),
                    "UPDATE db_im_private_chat_0 SET peer_user_id=900 WHERE chat_id=301");
            case "social" -> execute(fixture.social(),
                    "INSERT INTO db_friend(user_id,friend_user_id) VALUES(101,900)");
            case "account" -> execute(fixture.account(),
                    "UPDATE db_user SET email='ordinary@example.test' WHERE user_id=101");
            default -> throw new IllegalArgumentException("unknown owner");
        }
    }

    private static int targetCount(DatabaseFixture fixture, String owner) throws SQLException {
        return switch (owner) {
            case "message" -> count(fixture.message(),
                    "SELECT COUNT(*) FROM db_im_private_chat_0 WHERE user_id IN (101,102)");
            case "social" -> count(fixture.social(),
                    "SELECT COUNT(*) FROM db_friend WHERE user_id IN (101,102)");
            case "account" -> count(fixture.account(),
                    "SELECT COUNT(*) FROM db_user WHERE user_id IN (101,102)");
            default -> throw new IllegalArgumentException("unknown owner");
        };
    }

    private DataSource database(String name) throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        Connection connection = dataSource.getConnection();
        openConnections.add(connection);
        return dataSource;
    }

    private static void createAccount(DataSource dataSource) throws SQLException {
        execute(dataSource, "CREATE TABLE db_user(user_id BIGINT, email VARCHAR(100))");
        execute(dataSource, "INSERT INTO db_user VALUES(101,'loadtest-20260914-t3b1-user-0001@example.test')");
        execute(dataSource, "INSERT INTO db_user VALUES(102,'loadtest-20260914-t3b1-user-0002@example.test')");
        execute(dataSource, "INSERT INTO db_user VALUES(900,'ordinary@example.test')");
    }

    private static void createSocial(DataSource dataSource) throws SQLException {
        execute(dataSource, "CREATE TABLE db_friend(user_id BIGINT, friend_user_id BIGINT)");
        execute(dataSource, "CREATE TABLE db_im_group_member(group_id BIGINT, user_id BIGINT)");
        execute(dataSource, "CREATE TABLE db_im_group(group_id BIGINT, owner_id BIGINT, name VARCHAR(100))");
        execute(dataSource, "INSERT INTO db_friend VALUES(101,102),(102,101),(900,901)");
        execute(dataSource, "INSERT INTO db_im_group_member VALUES(201,101),(201,102),(900,900)");
        execute(dataSource, "INSERT INTO db_im_group VALUES(201,101,'loadtest-20260914-t3b1-group-0001'),(900,900,'ordinary')");
    }

    private static void createMessage(DataSource dataSource) throws SQLException {
        for (int shard = 0; shard < 8; shard++) {
            execute(dataSource, "CREATE TABLE db_im_private_inbox_message_" + shard
                    + "(message_id BIGINT,chat_id BIGINT,user_id BIGINT,sender_id BIGINT,token VARCHAR(100))");
            execute(dataSource, "CREATE TABLE db_im_group_inbox_message_" + shard
                    + "(message_id BIGINT,group_id BIGINT,chat_id BIGINT,user_id BIGINT,sender_id BIGINT,token VARCHAR(100))");
            execute(dataSource, "CREATE TABLE db_im_private_chat_" + shard
                    + "(chat_id BIGINT,user_id BIGINT,peer_user_id BIGINT)");
            execute(dataSource, "CREATE TABLE db_im_group_chat_" + shard
                    + "(chat_id BIGINT,group_id BIGINT,user_id BIGINT)");
        }
        execute(dataSource, "INSERT INTO db_im_private_chat_0 VALUES(301,101,102),(302,102,101),(900,900,901)");
        execute(dataSource, "INSERT INTO db_im_group_chat_0 VALUES(311,201,101),(312,201,102),(900,900,900)");
        execute(dataSource, "INSERT INTO db_im_private_inbox_message_0 VALUES"
                + "(401,301,101,101,'loadtest-20260914-t3b1:private:0001'),"
                + "(401,302,102,101,'loadtest-20260914-t3b1:private:0001'),"
                + "(900,900,900,900,'ordinary')");
        execute(dataSource, "INSERT INTO db_im_group_inbox_message_0 VALUES"
                + "(402,201,311,101,101,'loadtest-20260914-t3b1:group:0001'),"
                + "(402,201,312,102,101,'loadtest-20260914-t3b1:group:0001'),"
                + "(900,900,900,900,900,'ordinary')");
    }

    private static DataSource forbiddenDataSource() {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                    throw new AssertionError("dry-run opened a data source");
                });
    }

    private static DataSource identifiedRecording(DataSource delegate, List<SqlCall> calls, String catalog) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("getConnection")) {
                        return invoke(delegate, method, args);
                    }
                    Connection connection = (Connection) invoke(delegate, method, args);
                    return recordingConnection(connection, calls, false, catalog,
                            "jdbc:mysql://127.0.0.1:3306/" + catalog, new ArrayList<>());
                });
    }

    private static DataSource identityOverride(DataSource delegate, String catalog, String url,
            boolean metadataUnavailable, List<String> events) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("getConnection")) {
                        return invoke(delegate, method, args);
                    }
                    Connection connection = (Connection) invoke(delegate, method, args);
                    return identityConnection(connection, catalog, url, metadataUnavailable, events);
                });
    }

    private static DataSource interruptAfterFirstDelete(DataSource delegate, List<SqlCall> calls) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("getConnection")) {
                        return invoke(delegate, method, args);
                    }
                    Connection connection = (Connection) invoke(delegate, method, args);
                    return recordingConnection(connection, calls, true, "im_chat_message",
                            "jdbc:mysql://127.0.0.1:3306/im_chat_message", new ArrayList<>());
                });
    }

    private static Connection recordingConnection(Connection delegate, List<SqlCall> calls, boolean interrupt,
            String catalog, String url, List<String> events) {
        boolean[] injected = {false};
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getCatalog")) {
                        return catalog;
                    }
                    if (method.getName().equals("getMetaData")) {
                        return metadataWithUrl((DatabaseMetaData) invoke(delegate, method, args), url, false);
                    }
                    if (method.getName().equals("setAutoCommit")) {
                        events.add("setAutoCommit");
                    }
                    if (!method.getName().equals("prepareStatement")) {
                        return invoke(delegate, method, args);
                    }
                    String sql = (String) args[0];
                    PreparedStatement statement = (PreparedStatement) invoke(delegate, method, args);
                    Map<Integer, Object> parameters = new HashMap<>();
                    return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                            new Class<?>[]{PreparedStatement.class}, (preparedProxy, preparedMethod, preparedArgs) -> {
                                if (preparedMethod.getName().startsWith("set") && preparedArgs.length >= 2
                                        && preparedArgs[0] instanceof Integer index) {
                                    parameters.put(index, preparedArgs[1]);
                                }
                                if (preparedMethod.getName().equals("executeQuery")
                                        || preparedMethod.getName().equals("executeUpdate")) {
                                    calls.add(new SqlCall(sql, ordered(parameters)));
                                    Object result = invoke(statement, preparedMethod, preparedArgs);
                                    if (interrupt && !injected[0] && sql.startsWith("DELETE")
                                            && preparedMethod.getName().equals("executeUpdate")) {
                                        injected[0] = true;
                                        Thread.currentThread().interrupt();
                                    }
                                    return result;
                                }
                                return invoke(statement, preparedMethod, preparedArgs);
                            });
                });
    }

    private static Connection identityConnection(Connection delegate, String catalog, String url,
            boolean metadataUnavailable, List<String> events) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getCatalog")) {
                        return catalog;
                    }
                    if (method.getName().equals("getMetaData")) {
                        return metadataWithUrl((DatabaseMetaData) invoke(delegate, method, args), url,
                                metadataUnavailable);
                    }
                    if (method.getName().equals("setAutoCommit")) {
                        events.add("setAutoCommit");
                    }
                    return invoke(delegate, method, args);
                });
    }

    private static DatabaseMetaData metadataWithUrl(DatabaseMetaData delegate, String url, boolean unavailable) {
        return (DatabaseMetaData) Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),
                new Class<?>[]{DatabaseMetaData.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getURL")) {
                        if (unavailable) {
                            throw new SQLException("metadata unavailable");
                        }
                        return url;
                    }
                    return invoke(delegate, method, args);
                });
    }

    private static List<Object> ordered(Map<Integer, Object> parameters) {
        return parameters.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static void execute(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int count(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                java.sql.ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private record SqlCall(String sql, List<Object> parameters) {
    }

    private record DatabaseFixture(DataSource account, DataSource social, DataSource message, List<SqlCall> calls) {
    }

    private static final class IdempotentCrashingCheckpoint {
        private final String crashOwner;
        private final Set<String> persisted = new LinkedHashSet<>();
        private boolean crashed;

        private IdempotentCrashingCheckpoint(String crashOwner) {
            this.crashOwner = crashOwner;
        }

        void record(String owner) {
            if (!crashed && crashOwner.equals(owner)) {
                crashed = true;
                throw new IllegalStateException("checkpoint persistence interrupted");
            }
            persisted.add(owner);
        }

        Set<String> persisted() {
            return Set.copyOf(persisted);
        }
    }
}
