package com.co.kc.imchat.loadtest.cleanup;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class LoadTestSqlCleaner {

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1");
    private static final List<String> PRIVATE_INBOX_TABLES = tables("db_im_private_inbox_message_");
    private static final List<String> GROUP_INBOX_TABLES = tables("db_im_group_inbox_message_");
    private static final List<String> PRIVATE_CHAT_TABLES = tables("db_im_private_chat_");
    private static final List<String> GROUP_CHAT_TABLES = tables("db_im_group_chat_");
    private static final List<String> MESSAGE_TABLES = concat(
            PRIVATE_INBOX_TABLES, GROUP_INBOX_TABLES, PRIVATE_CHAT_TABLES, GROUP_CHAT_TABLES);
    private static final List<String> SOCIAL_TABLES = List.of(
            "db_friend", "db_im_group_member", "db_im_group");
    private static final List<String> ALL_TABLES = concat(MESSAGE_TABLES, SOCIAL_TABLES, List.of("db_user"));

    private final DataSource accountDataSource;
    private final DataSource socialDataSource;
    private final DataSource messageDataSource;
    private final Consumer<String> checkpoint;
    private final Set<String> emittedCheckpoints = new HashSet<>();

    LoadTestSqlCleaner(DataSource accountDataSource, DataSource socialDataSource, DataSource messageDataSource,
            Consumer<String> checkpoint) {
        this.accountDataSource = accountDataSource;
        this.socialDataSource = socialDataSource;
        this.messageDataSource = messageDataSource;
        this.checkpoint = checkpoint;
    }

    CleanupPlan dryRun(LoadTestBatchManifest manifest) {
        return plan(manifest);
    }

    static CleanupPlan plan(LoadTestBatchManifest manifest) {
        return new CleanupPlan(ALL_TABLES, manifest.users().size(), manifest.groups().size(),
                manifest.messages().size());
    }

    void execute(LoadTestBatchManifest manifest) throws SQLException, InterruptedException {
        execute(manifest, Set.of());
    }

    void verifyOwnership(LoadTestBatchManifest manifest) throws SQLException, InterruptedException {
        verifyOwner(accountDataSource, "im_chat_account", connection -> cleanAccount(connection, manifest, false));
        verifyOwner(socialDataSource, "im_chat_social", connection -> cleanSocial(connection, manifest, false));
        verifyOwner(messageDataSource, "im_chat_message", connection -> cleanMessage(connection, manifest, false));
    }

    private void verifyOwner(DataSource dataSource, String catalog, TransactionWork work)
            throws SQLException, InterruptedException {
        checkInterrupted();
        try (Connection connection = dataSource.getConnection()) {
            verifyConnectionIdentity(connection, catalog);
            work.run(connection);
        }
    }

    void execute(LoadTestBatchManifest manifest, Set<String> completedCheckpoints)
            throws SQLException, InterruptedException {
        checkInterrupted();
        if (!completedCheckpoints.contains("message")) {
            inTransaction(messageDataSource, "im_chat_message", connection -> cleanMessage(connection, manifest, true));
            emitCheckpoint("message");
        }
        checkInterrupted();
        if (!completedCheckpoints.contains("social")) {
            inTransaction(socialDataSource, "im_chat_social", connection -> cleanSocial(connection, manifest, true));
            emitCheckpoint("social");
        }
        checkInterrupted();
        if (!completedCheckpoints.contains("account")) {
            inTransaction(accountDataSource, "im_chat_account", connection -> cleanAccount(connection, manifest, true));
            emitCheckpoint("account");
        }
    }

    private void emitCheckpoint(String owner) {
        if (!emittedCheckpoints.add(owner)) {
            return;
        }
        try {
            checkpoint.accept(owner);
        } catch (RuntimeException exception) {
            emittedCheckpoints.remove(owner);
            throw exception;
        }
    }

    private void cleanMessage(Connection connection, LoadTestBatchManifest manifest, boolean deleteRows)
            throws SQLException, InterruptedException {
        Set<String> userIds = userIds(manifest);
        Set<String> groupIds = groupIds(manifest);
        Set<String> privateChatIds = manifest.privateChats().stream()
                .map(LoadTestBatchManifest.PrivateChat::chatId).collect(java.util.stream.Collectors.toSet());
        Set<String> groupChatIds = manifest.groupChats().stream()
                .map(LoadTestBatchManifest.GroupChat::chatId).collect(java.util.stream.Collectors.toSet());
        Map<String, LoadTestBatchManifest.Message> privateMessages = messages(manifest, "private");
        Map<String, LoadTestBatchManifest.Message> groupMessages = messages(manifest, "group");

        SchemaState state = schemaState("message",
                verifyPrivateChats(connection, manifest, userIds, privateChatIds),
                verifyGroupChats(connection, manifest, userIds, groupIds, groupChatIds),
                verifyPrivateInbox(connection, manifest, userIds, privateChatIds, privateMessages),
                verifyGroupInbox(connection, manifest, userIds, groupIds, groupChatIds, groupMessages));
        if (!deleteRows || state == SchemaState.ALL_ABSENT) {
            return;
        }

        for (String table : PRIVATE_INBOX_TABLES) {
            delete(connection, table, "user_id", userIds, "chat_id", privateChatIds,
                    "message_id", privateMessages.keySet(), "token", tokens(privateMessages));
        }
        for (String table : GROUP_INBOX_TABLES) {
            delete(connection, table, "user_id", userIds, "group_id", groupIds,
                    "chat_id", groupChatIds, "message_id", groupMessages.keySet(), "token", tokens(groupMessages));
        }
        for (String table : PRIVATE_CHAT_TABLES) {
            delete(connection, table, "user_id", userIds, "chat_id", privateChatIds,
                    "peer_user_id", userIds);
        }
        for (String table : GROUP_CHAT_TABLES) {
            delete(connection, table, "user_id", userIds, "group_id", groupIds, "chat_id", groupChatIds);
        }
    }

    private TargetState verifyPrivateChats(Connection connection, LoadTestBatchManifest manifest, Set<String> userIds,
            Set<String> chatIds) throws SQLException {
        Set<PrivateChatRow> expected = new HashSet<>();
        for (LoadTestBatchManifest.PrivateChat chat : manifest.privateChats()) {
            expected.add(new PrivateChatRow(chat.chatId(), chat.userId(), chat.peerUserId()));
        }
        Set<PrivateChatRow> actual = new HashSet<>();
        for (String table : PRIVATE_CHAT_TABLES) {
            Query query = selectIntersecting(table, "chat_id,user_id,peer_user_id",
                    "user_id", userIds, "peer_user_id", userIds, "chat_id", chatIds);
            try (PreparedStatement statement = prepare(connection, query); ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    PrivateChatRow row = new PrivateChatRow(rows.getString(1), rows.getString(2), rows.getString(3));
                    require(expected.contains(row), "private chat ownership precondition failed");
                    require(actual.add(row), "duplicate private chat target row");
                }
            }
        }
        return classify(expected, actual, "private chat");
    }

    private TargetState verifyGroupChats(Connection connection, LoadTestBatchManifest manifest, Set<String> userIds,
            Set<String> groupIds, Set<String> chatIds) throws SQLException {
        Set<GroupChatRow> expected = new HashSet<>();
        for (LoadTestBatchManifest.GroupChat chat : manifest.groupChats()) {
            expected.add(new GroupChatRow(chat.chatId(), chat.groupId(), chat.userId()));
        }
        Set<GroupChatRow> actual = new HashSet<>();
        for (String table : GROUP_CHAT_TABLES) {
            Query query = selectIntersecting(table, "chat_id,group_id,user_id",
                    "user_id", userIds, "group_id", groupIds, "chat_id", chatIds);
            try (PreparedStatement statement = prepare(connection, query); ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    GroupChatRow row = new GroupChatRow(rows.getString(1), rows.getString(2), rows.getString(3));
                    require(expected.contains(row), "group chat ownership precondition failed");
                    require(actual.add(row), "duplicate group chat target row");
                }
            }
        }
        return classify(expected, actual, "group chat");
    }

    private TargetState verifyPrivateInbox(Connection connection, LoadTestBatchManifest manifest, Set<String> userIds,
            Set<String> chatIds, Map<String, LoadTestBatchManifest.Message> messages) throws SQLException {
        Set<PrivateInboxRow> expected = expectedPrivateInbox(manifest, messages);
        Set<PrivateInboxRow> actual = new HashSet<>();
        Map<String, LoadTestBatchManifest.PrivateChat> chats = new HashMap<>();
        for (LoadTestBatchManifest.PrivateChat chat : manifest.privateChats()) {
            chats.put(chat.chatId(), chat);
        }
        for (String table : PRIVATE_INBOX_TABLES) {
            Query query = selectIntersecting(table, "message_id,chat_id,user_id,sender_id,token",
                    "user_id", userIds, "chat_id", chatIds, "message_id", messages.keySet(),
                    "token", tokens(messages));
            try (PreparedStatement statement = prepare(connection, query); ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    PrivateInboxRow row = new PrivateInboxRow(
                            rows.getString(1), rows.getString(2), rows.getString(3),
                            rows.getString(4), rows.getString(5));
                    require(chats.containsKey(row.chatId()) && expected.contains(row),
                            "private inbox ownership precondition failed");
                    require(actual.add(row), "duplicate private inbox target row");
                }
            }
        }
        return classify(expected, actual, "private inbox");
    }

    private TargetState verifyGroupInbox(Connection connection, LoadTestBatchManifest manifest, Set<String> userIds,
            Set<String> groupIds, Set<String> chatIds, Map<String, LoadTestBatchManifest.Message> messages)
            throws SQLException {
        Set<GroupInboxRow> expected = expectedGroupInbox(manifest, messages);
        Set<GroupInboxRow> actual = new HashSet<>();
        Map<String, LoadTestBatchManifest.GroupChat> chats = new HashMap<>();
        for (LoadTestBatchManifest.GroupChat chat : manifest.groupChats()) {
            chats.put(chat.chatId(), chat);
        }
        for (String table : GROUP_INBOX_TABLES) {
            Query query = selectIntersecting(table, "message_id,group_id,chat_id,user_id,sender_id,token",
                    "user_id", userIds, "group_id", groupIds, "chat_id", chatIds,
                    "message_id", messages.keySet(), "token", tokens(messages));
            try (PreparedStatement statement = prepare(connection, query); ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    GroupInboxRow row = new GroupInboxRow(
                            rows.getString(1), rows.getString(2), rows.getString(3),
                            rows.getString(4), rows.getString(5), rows.getString(6));
                    require(chats.containsKey(row.chatId()) && expected.contains(row),
                            "group inbox ownership precondition failed");
                    require(actual.add(row), "duplicate group inbox target row");
                }
            }
        }
        return classify(expected, actual, "group inbox");
    }

    private Set<PrivateInboxRow> expectedPrivateInbox(LoadTestBatchManifest manifest,
            Map<String, LoadTestBatchManifest.Message> messages) {
        Map<String, LoadTestBatchManifest.PrivateChat> chats = manifest.privateChats().stream()
                .collect(java.util.stream.Collectors.toMap(LoadTestBatchManifest.PrivateChat::chatId, chat -> chat));
        Set<PrivateInboxRow> expected = new HashSet<>();
        for (String receiptId : manifest.receiptIds()) {
            String[] parts = receiptId.split(":", -1);
            if (!"PRIVATE_MESSAGE_SEND".equals(parts[0]) || !messages.containsKey(parts[3])) {
                continue;
            }
            LoadTestBatchManifest.Message message = messages.get(parts[3]);
            LoadTestBatchManifest.PrivateChat receiverChat = chats.get(parts[2]);
            addPrivateInbox(expected, message, receiverChat);
            LoadTestBatchManifest.PrivateChat senderChat = manifest.privateChats().stream()
                    .filter(chat -> chat.userId().equals(message.senderUserId())
                            && chat.peerUserId().equals(receiverChat.userId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("private sender chat is missing"));
            addPrivateInbox(expected, message, senderChat);
        }
        return expected;
    }

    private static void addPrivateInbox(Set<PrivateInboxRow> expected, LoadTestBatchManifest.Message message,
            LoadTestBatchManifest.PrivateChat chat) {
        expected.add(new PrivateInboxRow(message.messageId(), chat.chatId(), chat.userId(),
                message.senderUserId(), message.correlationToken()));
    }

    private static Set<GroupInboxRow> expectedGroupInbox(LoadTestBatchManifest manifest,
            Map<String, LoadTestBatchManifest.Message> messages) {
        Set<GroupInboxRow> expected = new HashSet<>();
        for (LoadTestBatchManifest.Message message : messages.values()) {
            for (LoadTestBatchManifest.GroupChat chat : manifest.groupChats()) {
                if (chat.groupId().equals(message.groupId())) {
                    expected.add(new GroupInboxRow(message.messageId(), message.groupId(), chat.chatId(),
                            chat.userId(), message.senderUserId(), message.correlationToken()));
                }
            }
        }
        return expected;
    }

    private void cleanSocial(Connection connection, LoadTestBatchManifest manifest, boolean deleteRows)
            throws SQLException, InterruptedException {
        Set<String> userIds = userIds(manifest);
        Set<String> groupIds = groupIds(manifest);
        Set<RelationRow> expectedRelations = new HashSet<>();
        for (LoadTestBatchManifest.Relation relation : manifest.relations()) {
            expectedRelations.add(new RelationRow(relation.userId(), relation.friendUserId()));
        }
        Set<RelationRow> actualRelations = new HashSet<>();
        Query relations = selectIntersecting("db_friend", "user_id,friend_user_id",
                "user_id", userIds, "friend_user_id", userIds);
        try (PreparedStatement statement = prepare(connection, relations); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                RelationRow row = new RelationRow(rows.getString(1), rows.getString(2));
                require(expectedRelations.contains(row), "friend relation mixes batch and outside ownership");
                actualRelations.add(row);
            }
        }
        TargetState relationState = classify(expectedRelations, actualRelations, "friend relation");

        Set<MemberRow> expectedMembers = new HashSet<>();
        for (LoadTestBatchManifest.Group group : manifest.groups()) {
            for (String userId : group.memberUserIds()) {
                expectedMembers.add(new MemberRow(group.groupId(), userId));
            }
        }
        Set<MemberRow> actualMembers = new HashSet<>();
        Query members = selectIntersecting("db_im_group_member", "group_id,user_id",
                "group_id", groupIds, "user_id", userIds);
        try (PreparedStatement statement = prepare(connection, members); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                MemberRow row = new MemberRow(rows.getString(1), rows.getString(2));
                require(expectedMembers.contains(row), "group membership mixes batch and outside ownership");
                actualMembers.add(row);
            }
        }
        TargetState memberState = classify(expectedMembers, actualMembers, "group member");

        Set<GroupRow> expectedGroups = new HashSet<>();
        for (LoadTestBatchManifest.Group group : manifest.groups()) {
            expectedGroups.add(new GroupRow(group.groupId(), group.ownerUserId(), group.name()));
        }
        Set<GroupRow> actualGroups = new HashSet<>();
        Query groups = selectIntersecting("db_im_group", "group_id,owner_id,name",
                "group_id", groupIds, "owner_id", userIds);
        try (PreparedStatement statement = prepare(connection, groups); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                GroupRow row = new GroupRow(rows.getString(1), rows.getString(2), rows.getString(3));
                require(expectedGroups.contains(row), "group ownership precondition failed");
                actualGroups.add(row);
            }
        }
        TargetState groupState = classify(expectedGroups, actualGroups, "group");

        if (schemaState("social", relationState, memberState, groupState) == SchemaState.ALL_ABSENT || !deleteRows) {
            return;
        }

        delete(connection, "db_friend", "user_id", userIds, "friend_user_id", userIds);
        delete(connection, "db_im_group_member", "group_id", groupIds, "user_id", userIds);
        delete(connection, "db_im_group", "group_id", groupIds, "owner_id", userIds,
                "name", manifest.groups().stream().map(LoadTestBatchManifest.Group::name).collect(java.util.stream.Collectors.toSet()));
    }

    private void cleanAccount(Connection connection, LoadTestBatchManifest manifest, boolean deleteRows)
            throws SQLException, InterruptedException {
        Map<String, String> expected = new HashMap<>();
        for (LoadTestBatchManifest.User user : manifest.users()) {
            expected.put(user.userId(), user.email());
        }
        Set<UserRow> actual = new HashSet<>();
        Query users = selectIntersecting("db_user", "user_id,email", "user_id", expected.keySet(),
                "email", new HashSet<>(expected.values()));
        try (PreparedStatement statement = prepare(connection, users); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                UserRow row = new UserRow(rows.getString(1), rows.getString(2));
                require(row.email().equals(expected.get(row.userId())), "account ownership precondition failed");
                actual.add(row);
            }
        }
        Set<UserRow> expectedRows = new HashSet<>();
        expected.forEach((id, email) -> expectedRows.add(new UserRow(id, email)));
        if (schemaState("account", classify(expectedRows, actual, "account user")) == SchemaState.ALL_ABSENT || !deleteRows) {
            return;
        }
        delete(connection, "db_user", "user_id", expected.keySet(), "email", new HashSet<>(expected.values()));
    }

    private static <T> TargetState classify(Set<T> expected, Set<T> actual, String target) {
        if (expected.isEmpty()) {
            require(actual.isEmpty(), target + " ownership precondition failed");
            return TargetState.EMPTY_EXPECTED;
        }
        if (actual.isEmpty()) {
            return TargetState.ALL_ABSENT;
        }
        require(actual.equals(expected), target + " target rows are partial or mismatched");
        return TargetState.ALL_PRESENT_OWNED;
    }

    private static SchemaState schemaState(String owner, TargetState... states) {
        boolean present = false;
        boolean absent = false;
        for (TargetState state : states) {
            present |= state == TargetState.ALL_PRESENT_OWNED;
            absent |= state == TargetState.ALL_ABSENT;
        }
        require(!(present && absent), owner + " target rows are partially deleted");
        return present ? SchemaState.ALL_PRESENT_OWNED : SchemaState.ALL_ABSENT;
    }

    private void inTransaction(DataSource dataSource, String expectedCatalog, TransactionWork work)
            throws SQLException, InterruptedException {
        try (Connection connection = dataSource.getConnection()) {
            verifyConnectionIdentity(connection, expectedCatalog);
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                work.run(connection);
                checkInterrupted();
                connection.commit();
            } catch (SQLException | RuntimeException | InterruptedException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    private static void verifyConnectionIdentity(Connection connection, String expectedCatalog) {
        try {
            require(expectedCatalog.equals(connection.getCatalog()), "database catalog mismatch");
            DatabaseMetaData metadata = connection.getMetaData();
            require(metadata != null, "database metadata unavailable");
            String jdbcUrl = metadata.getURL();
            require(jdbcUrl != null && jdbcUrl.startsWith("jdbc:"), "database URL unavailable");
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            require("mysql".equals(uri.getScheme()), "database URL is not MySQL");
            require(LOOPBACK_HOSTS.contains(uri.getHost()), "database URL is not local");
            require(uri.getUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null,
                    "database URL is not safe");
            require(("/" + expectedCatalog).equals(uri.getPath()), "database URL catalog mismatch");
        } catch (SQLException | IllegalArgumentException exception) {
            throw new IllegalStateException("database connection identity verification failed for "
                    + expectedCatalog);
        }
    }

    private static Query selectIntersecting(String table, String columns, Object... predicates) {
        StringBuilder sql = new StringBuilder("SELECT ").append(columns).append(" FROM ").append(table)
                .append(" WHERE ");
        List<Object> parameters = new ArrayList<>();
        for (int index = 0; index < predicates.length; index += 2) {
            if (index > 0) {
                sql.append(" OR ");
            }
            appendIn(sql, parameters, (String) predicates[index], castSet(predicates[index + 1]));
        }
        return new Query(sql.toString(), parameters);
    }

    private static void delete(Connection connection, String table, Object... predicates)
            throws SQLException, InterruptedException {
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(table).append(" WHERE ");
        List<Object> parameters = new ArrayList<>();
        for (int index = 0; index < predicates.length; index += 2) {
            if (index > 0) {
                sql.append(" AND ");
            }
            appendIn(sql, parameters, (String) predicates[index], castSet(predicates[index + 1]));
        }
        try (PreparedStatement statement = prepare(connection, new Query(sql.toString(), parameters))) {
            statement.executeUpdate();
        }
        checkInterrupted();
    }

    private static void appendIn(StringBuilder sql, List<Object> parameters, String column, Set<String> values) {
        if (values.isEmpty()) {
            sql.append("? = 1");
            parameters.add(0);
            return;
        }
        sql.append(column).append(" IN (").append("?,".repeat(values.size()));
        sql.setLength(sql.length() - 1);
        sql.append(')');
        parameters.addAll(new LinkedHashSet<>(values));
    }

    private static PreparedStatement prepare(Connection connection, Query query) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(query.sql());
        for (int index = 0; index < query.parameters().size(); index++) {
            statement.setObject(index + 1, query.parameters().get(index));
        }
        return statement;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> castSet(Object value) {
        return (Set<String>) value;
    }

    private static Set<String> userIds(LoadTestBatchManifest manifest) {
        return manifest.users().stream().map(LoadTestBatchManifest.User::userId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> groupIds(LoadTestBatchManifest manifest) {
        return manifest.groups().stream().map(LoadTestBatchManifest.Group::groupId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static Map<String, LoadTestBatchManifest.Message> messages(LoadTestBatchManifest manifest, String type) {
        return manifest.messages().stream().filter(message -> message.type().equals(type))
                .collect(java.util.stream.Collectors.toMap(LoadTestBatchManifest.Message::messageId,
                        message -> message, (left, right) -> left, java.util.LinkedHashMap::new));
    }

    private static Set<String> tokens(Map<String, LoadTestBatchManifest.Message> messages) {
        return messages.values().stream().map(LoadTestBatchManifest.Message::correlationToken)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<String> tables(String prefix) {
        List<String> tables = new ArrayList<>();
        for (int shard = 0; shard < 8; shard++) {
            tables.add(prefix + shard);
        }
        return List.copyOf(tables);
    }

    @SafeVarargs
    private static <T> List<T> concat(List<T>... lists) {
        List<T> result = new ArrayList<>();
        for (List<T> list : lists) {
            result.addAll(list);
        }
        return List.copyOf(result);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void checkInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("cleanup interrupted");
        }
    }

    record CleanupPlan(List<String> tables, int userCount, int groupCount, int messageCount) {
        CleanupPlan {
            tables = List.copyOf(tables);
        }
    }

    private record Query(String sql, List<Object> parameters) {
    }

    private record UserRow(String userId, String email) {
    }

    private record RelationRow(String userId, String friendUserId) {
    }

    private record MemberRow(String groupId, String userId) {
    }

    private record GroupRow(String groupId, String ownerId, String name) {
    }

    private record PrivateChatRow(String chatId, String userId, String peerUserId) {
    }

    private record GroupChatRow(String chatId, String groupId, String userId) {
    }

    private record PrivateInboxRow(String messageId, String chatId, String userId, String senderId, String token) {
    }

    private record GroupInboxRow(String messageId, String groupId, String chatId, String userId, String senderId,
                                 String token) {
    }

    private enum TargetState {
        ALL_PRESENT_OWNED,
        ALL_ABSENT,
        EMPTY_EXPECTED
    }

    private enum SchemaState {
        ALL_PRESENT_OWNED,
        ALL_ABSENT
    }

    @FunctionalInterface
    private interface TransactionWork {
        void run(Connection connection) throws SQLException, InterruptedException;
    }
}
