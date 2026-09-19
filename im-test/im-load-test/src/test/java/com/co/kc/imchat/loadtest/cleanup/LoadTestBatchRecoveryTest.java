package com.co.kc.imchat.loadtest.cleanup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoadTestBatchRecoveryTest {
    private static final String BATCH = "20260914-recovery";
    private static final String PREFIX = "loadtest-" + BATCH;
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path directory;

    @Test
    void recoverOrReadAtomicallyPersistsDigestBoundOwnerOnlySnapshot() throws Exception {
        Fixture db = prepared();
        Path journal = fullJournal();
        Path snapshot = directory.resolve("recovery.json");

        LoadTestBatchManifest recovered = db.recovery().recoverOrRead(empty(), journal, snapshot);

        assertTrue(Files.isRegularFile(snapshot));
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(snapshot));
        JsonNode envelope = JSON.readTree(snapshot.toFile());
        Set<String> fields = new java.util.HashSet<>();
        envelope.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("schemaVersion", "batchId", "seedSha256", "journalSha256",
                "recoveredSha256", "recovered"), fields);
        assertEquals(1, envelope.path("schemaVersion").intValue());
        assertEquals(BATCH, envelope.path("batchId").textValue());
        assertEquals(sha(JSON.writeValueAsBytes(empty())), envelope.path("seedSha256").textValue());
        assertEquals(sha(Files.readAllBytes(journal)), envelope.path("journalSha256").textValue());
        assertEquals(sha(JSON.writeValueAsBytes(envelope.get("recovered"))),
                envelope.path("recoveredSha256").textValue());
        assertEquals(recovered, JSON.treeToValue(envelope.get("recovered"), LoadTestBatchManifest.class));
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            assertEquals(Set.of(journal, snapshot), files.collect(java.util.stream.Collectors.toSet()));
        }
    }

    @Test
    void snapshotCannotDropARecoveredReceiptWithoutInvalidatingItsDigest() throws Exception {
        Fixture db = prepared();
        Path journal = fullJournal();
        Path snapshot = directory.resolve("receipt-tamper.json");
        db.recovery().recoverOrRead(empty(), journal, snapshot);
        JsonNode envelope = JSON.readTree(snapshot.toFile());
        ((com.fasterxml.jackson.databind.node.ArrayNode) envelope.path("recovered").path("receiptIds")).remove(0);
        Files.writeString(snapshot, JSON.writeValueAsString(envelope));

        LoadTestBatchRecovery retry = new LoadTestBatchRecovery(forbiddenDataSource(), forbiddenDataSource(),
                forbiddenDataSource());
        assertThrows(IllegalStateException.class,
                () -> retry.recoverOrRead(empty(), journal, snapshot));
    }

    @Test
    void existingSnapshotIsReadWithoutOpeningDatabaseAfterPartialCleanup() throws Exception {
        Fixture db = prepared();
        Path journal = fullJournal();
        Path snapshot = directory.resolve("recovery.json");
        LoadTestBatchManifest expected = db.recovery().recoverOrRead(empty(), journal, snapshot);
        LoadTestBatchRecovery retry = new LoadTestBatchRecovery(forbiddenDataSource(), forbiddenDataSource(),
                forbiddenDataSource());

        assertEquals(expected, retry.recoverOrRead(empty(), journal, snapshot));
    }

    @Test
    void snapshotRejectsChangedSeedOrJournalBeforeOpeningDatabase() throws Exception {
        Fixture db = prepared();
        Path journal = fullJournal();
        Path snapshot = directory.resolve("recovery.json");
        db.recovery().recoverOrRead(empty(), journal, snapshot);
        LoadTestBatchRecovery retry = new LoadTestBatchRecovery(forbiddenDataSource(), forbiddenDataSource(),
                forbiddenDataSource());
        LoadTestBatchManifest changedSeed = copy(empty(),
                List.of(new LoadTestBatchManifest.User("101", email("101"), null)), List.of(), List.of());

        assertThrows(IllegalStateException.class, () -> retry.recoverOrRead(changedSeed, journal, snapshot));
        Files.writeString(journal, "\n", java.nio.file.StandardOpenOption.APPEND);
        assertThrows(IllegalStateException.class, () -> retry.recoverOrRead(empty(), journal, snapshot));
    }

    @Test
    void snapshotRejectsTamperUnknownDuplicateAndPartialJson() throws Exception {
        for (String corruption : List.of("tamper", "unknown", "duplicate", "partial")) {
            Fixture db = prepared();
            Path journal = fullJournal();
            Path snapshot = directory.resolve(corruption + ".json");
            db.recovery().recoverOrRead(empty(), journal, snapshot);
            String json = Files.readString(snapshot);
            switch (corruption) {
                case "tamper" -> json = json.replaceFirst("\"userId\":\"101\"", "\"userId\":\"900\"");
                case "unknown" -> json = json.replaceFirst("\\{", "{\"unknown\":true,");
                case "duplicate" -> json = json.replaceFirst("\"batchId\":", "\"batchId\":\"forged\",\"batchId\":");
                case "partial" -> json = json.substring(0, json.length() / 2);
                default -> throw new IllegalStateException();
            }
            Files.writeString(snapshot, json);
            LoadTestBatchRecovery retry = new LoadTestBatchRecovery(forbiddenDataSource(), forbiddenDataSource(),
                    forbiddenDataSource());
            assertThrows(Exception.class, () -> retry.recoverOrRead(empty(), journal, snapshot), corruption);
        }
    }

    @Test
    void incompleteSignupSnapshotRoundTripsWithoutScenarioValidationOrDatabase() throws Exception {
        Fixture db = new Fixture();
        db.user("101");
        Path journal = journal(signup("101", true));
        Path snapshot = directory.resolve("signup-only.json");
        LoadTestBatchManifest recovered = db.recovery().recoverOrRead(empty(), journal, snapshot);
        LoadTestBatchRecovery retry = new LoadTestBatchRecovery(forbiddenDataSource(), forbiddenDataSource(),
                forbiddenDataSource());

        LoadTestBatchManifest read = retry.recoverOrRead(empty(), journal, snapshot);

        assertEquals(recovered, read);
        assertNull(read.users().getFirst().cohortId());
        assertTrue(read.cohorts().isEmpty());
        assertTrue(read.groups().isEmpty());
    }

    @Test
    void discoversCommittedSignupMissingFromSeedAndUnassignedPreparation() throws Exception {
        Fixture db = new Fixture();
        db.user("101");
        db.user("102");
        Path journal = journal(signup("101", true), signup("102", false));
        LoadTestBatchManifest recovered = db.recovery().recover(copy(empty(),
                List.of(new LoadTestBatchManifest.User("101", email("101"), "omitted-cohort")), List.of(), List.of()), journal);
        assertEquals(Set.of("101", "102"), recovered.users().stream()
                .map(LoadTestBatchManifest.User::userId).collect(java.util.stream.Collectors.toSet()));
        assertTrue(recovered.cohorts().isEmpty());
        assertTrue(recovered.groups().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> recovered.users().clear());
        assertReadOnly(db);
    }

    @Test
    void recoversEntireOmittedCohortAndReceiptsFromDurableInbox() throws Exception {
        Fixture db = prepared();
        LoadTestBatchManifest recovered = db.recovery().recover(empty(), fullJournal());
        assertEquals(2, recovered.users().size());
        assertEquals(1, recovered.groups().size());
        assertEquals(List.of("101", "102"), recovered.groups().getFirst().memberUserIds());
        assertEquals(2, recovered.privateChats().size());
        assertEquals(2, recovered.groupChats().size());
        assertEquals(Set.of("PRIVATE_MESSAGE_SEND:102:302:401", "GROUP_MESSAGE_SEND:102:312:402"),
                Set.copyOf(recovered.receiptIds()));
        assertEquals(32, db.calls.stream().filter(call -> call.table().startsWith("db_im_")
                && !Set.of("db_im_group", "db_im_group_member").contains(call.table()))
                .map(Call::table).distinct().count());
        assertReadOnly(db);
    }

    @Test
    void acceptsCompletedGroupWithOnlySocialCommitResult() throws Exception {
        Fixture db = prepared();
        String operationId = "group-1";
        Map<String, Object> result = Map.of("groupId", "201");
        Path journal = journal(signup("101", true), signup("102", true),
                record(operationId, "intent", "createGroup", Map.of("ownership",
                        Map.of("ownerId", "101", "memberIds", List.of("102"),
                                "groupName", PREFIX + "-group"))),
                record(operationId, "invoked", "createGroup", Map.of("attempt", 1)),
                record(operationId, "reconciled", "createGroup", Map.of("attempt", 1,
                        "outcome", "committed", "result", result,
                        "evidence", Map.of("source", "local-db-social"))),
                record(operationId, "completed", "createGroup", Map.of("attempt", 1,
                        "result", result)));

        LoadTestBatchManifest recovered = db.recovery().recover(empty(), journal);

        assertEquals("201", recovered.groups().getFirst().groupId());
        assertEquals(2, recovered.groupChats().size());
        assertEquals(Set.of("GROUP_MESSAGE_SEND:102:312:402"), recovered.receiptIds().stream()
                .filter(receipt -> receipt.startsWith("GROUP_MESSAGE_SEND:"))
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void recoversActualFixedWidthCorrelationPayload() throws Exception {
        Fixture db = prepared();
        String content = correlation(BATCH, "42", "measure", "9007199254740993123");
        assertEquals(256, content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        db.rows.get("db_im_private_inbox_message_0").forEach(row -> row.put("content", content));

        LoadTestBatchManifest recovered = db.recovery().recover(empty(), fullJournal());

        assertTrue(recovered.messages().stream().anyMatch(message -> message.messageId().equals("401")));
    }

    @Test
    void rejectsMalformedFixedWidthCorrelationPayload() throws Exception {
        for (String content : List.of(
                correlation(BATCH, "1", "warmup", "7").substring(1),
                correlation("other-batch", "1", "warmup", "7"),
                correlation(BATCH, "1", "warmup", "7").replace("phase=warmup ", "phase=invalid"),
                correlation(BATCH, "1", "warmup", "7").replaceFirst("padding=\\.", "padding=x"))) {
            Fixture db = prepared();
            db.rows.get("db_im_private_inbox_message_0").forEach(row -> row.put("content", content));
            assertThrows(IllegalStateException.class, () -> db.recovery().recover(empty(), fullJournal()));
        }
    }

    @Test
    void recoversPureSignupOnlyPreparationWithoutCohortOrGroup() throws Exception {
        Fixture db = new Fixture();
        db.user("101");

        LoadTestBatchManifest recovered = db.recovery().recover(empty(), journal(signup("101", true)));

        assertEquals(List.of("101"), recovered.users().stream().map(LoadTestBatchManifest.User::userId).toList());
        assertNull(recovered.users().getFirst().cohortId());
        assertTrue(recovered.cohorts().isEmpty());
        assertTrue(recovered.groups().isEmpty());
        assertTrue(recovered.privateChats().isEmpty());
        assertTrue(recovered.groupChats().isEmpty());
        assertTrue(recovered.messages().isEmpty());
        assertTrue(recovered.receiptIds().isEmpty());
        assertReadOnly(db);
    }

    @Test
    void acceptsEmptyFrozenBatchWhenEverySignupIsUncommitted() throws Exception {
        Fixture db = new Fixture();
        String operation = "signup-empty";
        Map<String, Object> reconciliation = new LinkedHashMap<>();
        reconciliation.put("attempt", 1);
        reconciliation.put("outcome", "not_committed");
        reconciliation.put("result", null);
        reconciliation.put("evidence", Map.of("source", "database"));
        Path journal = journal(
                record(operation, "intent", "signUp", Map.of("ownership",
                        Map.of("email", email("101"), "username", username("101")))),
                record(operation, "invoked", "signUp", Map.of("attempt", 1)),
                record(operation, "reconciled", "signUp", reconciliation));

        assertEquals(empty(), db.recovery().recover(empty(), journal));
        assertReadOnly(db);
    }

    @Test
    void recoversProductionGroupCreatedTokenWithoutReplacingIt() throws Exception {
        Fixture db = prepared();
        db.rows.put("db_im_group_inbox_message_0", new ArrayList<>());
        db.groupMessage("403", "system:group_created:201", "7", "群聊已创建");
        LoadTestBatchManifest recovered = db.recovery().recover(empty(), fullJournal());
        assertEquals("system:group_created:201", recovered.messages().stream()
                .filter(message -> message.messageId().equals("403")).findFirst().orElseThrow().correlationToken());
        assertTrue(recovered.receiptIds().contains("GROUP_MESSAGE_SEND:102:312:403"));
    }

    @Test
    void preservesSeedReceiptWhenReceiverInboxExpiredButMessageAndChatRemain() throws Exception {
        Fixture db = prepared();
        db.rows.get("db_im_private_inbox_message_0").removeIf(row -> row.get("user_id").equals("102"));
        assertTrue(db.recovery().recover(seed(), fullJournal()).receiptIds()
                .contains("PRIVATE_MESSAGE_SEND:102:302:401"));
    }

    @Test
    void rejectsForgedSeedRootRatherThanTrustingItsPrefixedName() throws Exception {
        Fixture db = prepared();
        LoadTestBatchManifest valid = seed();
        LoadTestBatchManifest forged = copy(valid, List.of(new LoadTestBatchManifest.User(
                "900", email("101"), "cohort-1")), valid.groups(), valid.receiptIds());
        assertThrows(IllegalStateException.class, () -> db.recovery().recover(forged, fullJournal()));
        assertReadOnly(db);
    }

    @Test
    void rejectsForgedGroupAndSeedEmail() throws Exception {
        Fixture db = prepared();
        LoadTestBatchManifest valid = seed();
        assertThrows(IllegalStateException.class, () -> db.recovery().recover(copy(valid, valid.users(),
                List.of(new LoadTestBatchManifest.Group("900", PREFIX + "-group", "101", List.of("101", "102"))),
                valid.receiptIds()), fullJournal()));
        assertThrows(IllegalStateException.class, () -> db.recovery().recover(copy(valid,
                List.of(new LoadTestBatchManifest.User("101", PREFIX + "-forged", "cohort-1")),
                valid.groups(), valid.receiptIds()), fullJournal()));
    }

    @Test
    void rejectsOutsideFriendMemberChatSenderAndTokenRows() throws Exception {
        for (String contamination : List.of("friend", "member", "privateChat", "groupChat", "sender", "token",
                "content", "systemType", "systemOwner", "systemGroup")) {
            Fixture db = prepared();
            switch (contamination) {
                case "friend" -> db.add("db_friend", "user_id", "101", "friend_user_id", "900");
                case "member" -> db.add("db_im_group_member", "group_id", "201", "user_id", "900");
                case "privateChat" -> db.add("db_im_private_chat_7", "chat_id", "901", "user_id", "900", "peer_user_id", "101");
                case "groupChat" -> db.add("db_im_group_chat_7", "chat_id", "901", "user_id", "101", "group_id", "900");
                case "sender" -> db.rows.get("db_im_group_inbox_message_0").getFirst().put("sender_id", "900");
                case "token" -> db.rows.get("db_im_private_inbox_message_0").getFirst().put("token", "ordinary");
                case "content" -> db.rows.get("db_im_private_inbox_message_0").getFirst().put("content", "ordinary");
                default -> {
                    db.rows.put("db_im_group_inbox_message_0", new ArrayList<>());
                    db.groupMessage("403", "system:group_created:" + (contamination.equals("systemGroup") ? "900" : "201"),
                            contamination.equals("systemType") ? "1" : "7", "群聊已创建");
                    if (contamination.equals("systemOwner")) {
                        db.rows.get("db_im_group_inbox_message_0").forEach(row -> row.put("sender_id", "102"));
                    }
                }
            }
            assertThrows(IllegalStateException.class, () -> db.recovery().recover(empty(), fullJournal()), contamination);
            assertReadOnly(db);
        }
    }

    @Test
    void rejectsInboxOwnedOnlyBySenderPredicate() throws Exception {
        Fixture db = prepared();
        db.add("db_im_private_inbox_message_7", "message_id", "901", "chat_id", "902", "user_id", "900",
                "sender_id", "101", "token", PREFIX + ":private:outside", "type", "1", "content", PREFIX + ":outside");
        assertThrows(IllegalStateException.class, () -> db.recovery().recover(empty(), fullJournal()));
    }

    @Test
    void rejectsMissingWrongBatchUnknownFieldAndInvalidJournalTransitionsBeforeConnections() throws Exception {
        for (String invalid : List.of("", "{}\n", signup("101", true).replace(BATCH, "other"),
                signup("101", true).replace("\"type\":\"intent\"", "\"extra\":1,\"type\":\"intent\""),
                signup("101", true).replace("\"type\":\"invoked\"", "\"type\":\"completed\""),
                signup("101", true) + "{\"truncated\":")) {
            Fixture db = new Fixture();
            assertThrows(Exception.class, () -> db.recovery().recover(empty(), journal(invalid)));
            verify(db.account, never()).getConnection();
        }
        Fixture db = new Fixture();
        assertThrows(Exception.class, () -> db.recovery().recover(empty(), directory.resolve("missing")));
        verify(db.account, never()).getConnection();
    }

    @Test
    void verifiesEveryOwnerConnectionBeforeAnySql() throws Exception {
        for (String owner : List.of("account", "social", "message")) {
            for (String url : List.of("jdbc:mysql://db.example:3306/im_chat_" + owner,
                    "jdbc:mysql://localhost:3306/wrong", "jdbc:mysql://localhost:3306/im_chat_" + owner + "?socketFactory=evil")) {
                Fixture db = prepared();
                when(db.connections.get(owner).getMetaData().getURL()).thenReturn(url);
                assertThrows(IllegalStateException.class, () -> db.recovery().recover(empty(), fullJournal()));
                assertTrue(db.calls.isEmpty());
                assertReadOnly(db);
            }
        }
    }

    @Test
    void escapesLikeWildcardsAndChecksActualBoundaryAndCanonicalIds() throws Exception {
        Fixture db = new Fixture();
        db.user("101");
        db.recovery().recover(empty(), journal(signup("101", true)));
        Call discovery = db.calls.stream().filter(call -> call.sql().contains("email LIKE")).findFirst().orElseThrow();
        assertEquals(PREFIX + "-%", discovery.parameters().getFirst());
        assertTrue(discovery.sql().contains("ESCAPE"));
        for (String invalidId : List.of("0", "01", "9223372036854775808")) {
            Fixture invalid = new Fixture();
            invalid.add("db_user", "user_id", invalidId, "email", email("101"), "username", username("101"));
            assertThrows(IllegalStateException.class, () -> invalid.recovery().recover(empty(), journal(signup("101", false))));
        }
    }

    @Test
    void rejectsUnresolvedSignupWhoseUsernameDoesNotMatchWal() throws Exception {
        Fixture db = new Fixture();
        db.user("101");
        db.rows.get("db_user").getFirst().put("username", PREFIX + "-forged");
        assertThrows(IllegalStateException.class, () -> db.recovery().recover(empty(), journal(signup("101", false))));
    }

    @Test
    void rejectsPrivateMessageCopiesForDifferentOwnedPeers() throws Exception {
        Fixture db = prepared();
        db.user("103");
        db.add("db_friend", "user_id", "103", "friend_user_id", "101");
        db.add("db_im_private_chat_7", "chat_id", "303", "user_id", "103", "peer_user_id", "101");
        db.add("db_im_private_inbox_message_7", "message_id", "401", "chat_id", "303", "user_id", "103",
                "sender_id", "101", "token", PREFIX + ":private:1", "type", "1", "content", PREFIX + ":private:1");
        assertThrows(IllegalStateException.class, () -> db.recovery().recover(empty(), fullJournal()));
    }

    @Test
    void verifiesCatalogAndUnavailableMetadataBeforeSql() throws Exception {
        for (String owner : List.of("account", "social", "message")) {
            Fixture wrongCatalog = prepared();
            when(wrongCatalog.connections.get(owner).getCatalog()).thenReturn("im_chat_ordinary");
            assertThrows(IllegalStateException.class, () -> wrongCatalog.recovery().recover(empty(), fullJournal()));
            assertTrue(wrongCatalog.calls.isEmpty());
            Fixture missingMetadata = prepared();
            when(missingMetadata.connections.get(owner).getMetaData()).thenReturn(null);
            assertThrows(IllegalStateException.class, () -> missingMetadata.recovery().recover(empty(), fullJournal()));
            assertTrue(missingMetadata.calls.isEmpty());
        }
    }

    @Test
    void escapesWildcardBatchAndRejectsRowsReturnedOutsideActualBoundary() throws Exception {
        String unusualBatch = "wild_%\\batch";
        String unusualPrefix = "loadtest-" + unusualBatch;
        Fixture db = new Fixture();
        db.user("101");
        db.rows.get("db_user").getFirst().replaceAll((field, value) -> value.replace(PREFIX, unusualPrefix));
        StringBuilder lines = new StringBuilder();
        for (String line : signup("101", true).split("\n")) {
            com.fasterxml.jackson.databind.node.ObjectNode record = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(line);
            record.put("batchId", unusualBatch);
            if (record.has("ownership")) {
                com.fasterxml.jackson.databind.node.ObjectNode ownership = (com.fasterxml.jackson.databind.node.ObjectNode) record.get("ownership");
                ownership.put("email", unusualPrefix + "-user-101@example.test");
                ownership.put("username", unusualPrefix + "-user-101");
            }
            lines.append(JSON.writeValueAsString(record)).append('\n');
        }
        LoadTestBatchManifest custom = new LoadTestBatchManifest(1, unusualBatch, unusualPrefix, empty().schemas(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertEquals(1, db.recovery().recover(custom, journal(lines.toString())).users().size());
        Call discovery = db.calls.stream().filter(call -> call.sql().contains("email LIKE")).findFirst().orElseThrow();
        assertEquals("loadtest-wild\\_\\%\\\\batch-%", discovery.parameters().getFirst());

        Fixture boundary = new Fixture();
        boundary.user("101");
        boundary.rows.get("db_user").getFirst().put("email", PREFIX + "other-user-101@example.test");
        boundary.ignoreFiltering = true;
        assertThrows(IllegalStateException.class, () -> boundary.recovery().recover(empty(), journal(signup("101", true))));
    }

    @Test
    void rejectsSeedReceiptWithoutDurableProofAndConflictingInboxCopies() throws Exception {
        Fixture db = prepared();
        assertThrows(IllegalStateException.class, () -> db.recovery().recover(copy(seed(), seed().users(), seed().groups(),
                List.of("PRIVATE_MESSAGE_SEND:102:302:999")), fullJournal()));
        for (String field : List.of("content", "sender_id", "token")) {
            Fixture conflict = prepared();
            conflict.rows.get("db_im_group_inbox_message_0").getLast().put(field,
                    field.equals("sender_id") ? "102" : PREFIX + ":different");
            assertThrows(IllegalStateException.class, () -> conflict.recovery().recover(empty(), fullJournal()));
        }
    }

    @Test
    void rejectsInvalidWalPayloadsAndUnconfirmedOwnershipReferences() throws Exception {
        for (String invalid : List.of(signup("101", true).replace("\"userId\":\"101\"", "\"userId\":101"),
                signup("101", true).replace("\"userId\":\"101\"", "\"userId\":\"01\""),
                signup("101", true).replace("\"attempt\":1", "\"attempt\":0"),
                signup("101", true).replace("\"signUp\"", "\"deleteUser\""),
                signup("101", true).replace("\"source\":\"database\"", "\"accessToken\":\"forbidden\""),
                signup("101", true).replace("\"schemaVersion\":1", "\"schemaVersion\":18446744073709551617"),
                signup("101", false) + record("friend-1", "intent", "addFriend", Map.of("ownership", Map.of("userId", "101", "friendUserId", "102"))))) {
            Fixture db = new Fixture();
            assertThrows(Exception.class, () -> db.recovery().recover(empty(), journal(invalid)));
            verify(db.account, never()).getConnection();
        }
    }

    @Test
    void alreadyCleanedRequiresFullSeedAndConfirmedWalUsersAndNoRemainingRows() throws Exception {
        Fixture db = new Fixture();
        assertEquals(seed(), db.recovery().recover(seed(), fullJournal()));
        assertReadOnly(db);
        assertThrows(IllegalStateException.class, () -> new Fixture().recovery().recover(empty(), fullJournal()));
        assertThrows(IllegalStateException.class, () -> new Fixture().recovery().recover(seed(),
                journal(signup("101", true), signup("102", false))));
        Fixture remaining = new Fixture();
        remaining.add("db_friend", "user_id", "101", "friend_user_id", "102");
        assertThrows(IllegalStateException.class, () -> remaining.recovery().recover(seed(), fullJournal()));
    }

    private static LoadTestBatchManifest empty() {
        return new LoadTestBatchManifest(1, BATCH, PREFIX,
                new LoadTestBatchManifest.Schemas("im_chat_account", "im_chat_social", "im_chat_message"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static LoadTestBatchManifest seed() {
        LoadTestBatchManifest root = empty();
        return new LoadTestBatchManifest(1, BATCH, PREFIX, root.schemas(),
                List.of(new LoadTestBatchManifest.User("101", email("101"), "cohort-1"),
                        new LoadTestBatchManifest.User("102", email("102"), "cohort-1")),
                List.of(new LoadTestBatchManifest.Cohort("cohort-1", List.of("101", "102"), "201")),
                List.of(new LoadTestBatchManifest.Group("201", PREFIX + "-group", "101", List.of("101", "102"))),
                List.of(new LoadTestBatchManifest.Relation("101", "102"), new LoadTestBatchManifest.Relation("102", "101")),
                List.of(new LoadTestBatchManifest.PrivateChat("301", "101", "102"), new LoadTestBatchManifest.PrivateChat("302", "102", "101")),
                List.of(new LoadTestBatchManifest.GroupChat("311", "201", "101"), new LoadTestBatchManifest.GroupChat("312", "201", "102")),
                List.of(new LoadTestBatchManifest.Message("401", "private", "101", null, PREFIX + ":private:1"),
                        new LoadTestBatchManifest.Message("402", "group", "101", "201", PREFIX + ":group:1")),
                List.of("PRIVATE_MESSAGE_SEND:102:302:401", "GROUP_MESSAGE_SEND:102:312:402"));
    }

    private static LoadTestBatchManifest copy(LoadTestBatchManifest seed, List<LoadTestBatchManifest.User> users,
            List<LoadTestBatchManifest.Group> groups, List<String> receipts) {
        return new LoadTestBatchManifest(seed.version(), seed.batchId(), seed.prefix(), seed.schemas(), users,
                seed.cohorts(), groups, seed.relations(), seed.privateChats(), seed.groupChats(), seed.messages(), receipts);
    }

    private Path fullJournal() throws Exception {
        String group = record("group-1", "intent", "createGroup", Map.of("ownership",
                Map.of("ownerId", "101", "memberIds", List.of("102"), "groupName", PREFIX + "-group")));
        return journal(signup("101", true), signup("102", true), group);
    }

    private Path journal(String... records) throws Exception {
        Path path = Files.createTempFile(directory, "wal-", ".jsonl");
        Files.writeString(path, String.join("", records));
        return path;
    }

    private static String signup(String id, boolean confirmed) throws Exception {
        String operation = "signup-" + id;
        String lines = record(operation, "intent", "signUp", Map.of("ownership", Map.of("email", email(id), "username", username(id))))
                + record(operation, "invoked", "signUp", Map.of("attempt", 1));
        if (confirmed) {
            lines += record(operation, "reconciled", "signUp", Map.of("attempt", 1, "outcome", "committed",
                    "result", Map.of("userId", id), "evidence", Map.of("source", "database")))
                    + record(operation, "completed", "signUp", Map.of("attempt", 1, "result", Map.of("userId", id)));
        }
        return lines;
    }

    private static String record(String id, String type, String operation, Map<String, Object> fields) throws Exception {
        Map<String, Object> value = new LinkedHashMap<>(Map.of("schemaVersion", 1, "batchId", BATCH,
                "operationId", id, "type", type, "operation", operation));
        value.putAll(fields);
        return JSON.writeValueAsString(value) + "\n";
    }

    private static String email(String id) { return username(id) + "@example.test"; }
    private static String username(String id) { return PREFIX + "-user-" + id; }

    private static String correlation(String batch, String round, String phase, String sequence) {
        String header = "IMLT1|batch=" + String.format("%-32s", batch)
                + "|round=" + String.format("%06d", Long.parseLong(round))
                + "|phase=" + String.format("%-7s", phase)
                + "|sequence=" + String.format("%019d", new java.math.BigInteger(sequence)) + "|padding=";
        return header + ".".repeat(256 - header.length());
    }

    private static String sha(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static Fixture prepared() throws Exception {
        Fixture db = new Fixture();
        db.user("101"); db.user("102");
        db.add("db_friend", "user_id", "101", "friend_user_id", "102");
        db.add("db_friend", "user_id", "102", "friend_user_id", "101");
        db.add("db_im_group", "group_id", "201", "owner_id", "101", "name", PREFIX + "-group");
        for (String id : List.of("101", "102")) {
            db.add("db_im_group_member", "group_id", "201", "user_id", id);
            db.add("db_im_private_chat_0", "chat_id", id.equals("101") ? "301" : "302", "user_id", id,
                    "peer_user_id", id.equals("101") ? "102" : "101");
            db.add("db_im_group_chat_0", "chat_id", id.equals("101") ? "311" : "312", "group_id", "201", "user_id", id);
            db.add("db_im_private_inbox_message_0", "message_id", "401", "chat_id", id.equals("101") ? "301" : "302",
                    "user_id", id, "sender_id", "101", "token", PREFIX + ":private:1", "type", "1", "content",
                    correlation(BATCH, "1", "measure", "1"));
        }
        db.groupMessage("402", PREFIX + ":group:1", "1", correlation(BATCH, "1", "measure", "2"));
        return db;
    }

    private static void assertReadOnly(Fixture db) throws Exception {
        for (Connection connection : db.connections.values()) {
            verify(connection, never()).commit(); verify(connection, never()).rollback();
            verify(connection, never()).setAutoCommit(anyBoolean());
            verify(connection, never()).createStatement();
        }
        assertTrue(db.calls.stream().allMatch(call -> call.sql().startsWith("SELECT ")));
    }

    private static DataSource forbiddenDataSource() {
        DataSource source = mock(DataSource.class);
        try {
            when(source.getConnection()).thenThrow(new AssertionError("snapshot retry opened database"));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        return source;
    }

    private record Call(String sql, String table, List<Object> parameters) { }

    // JDBC is substituted completely: this fixture proves recovery logic, not a MySQL driver/server.
    private static final class Fixture {
        final Map<String, List<Map<String, String>>> rows = new LinkedHashMap<>();
        final Map<String, Connection> connections = new LinkedHashMap<>();
        final List<Call> calls = new ArrayList<>();
        boolean ignoreFiltering;
        final DataSource account = source("account");
        final DataSource social = source("social");
        final DataSource message = source("message");

        Fixture() throws Exception { }

        LoadTestBatchRecovery recovery() { return new LoadTestBatchRecovery(account, social, message); }
        void user(String id) { add("db_user", "user_id", id, "email", email(id), "username", username(id)); }
        void add(String table, String... fields) {
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < fields.length; i += 2) { row.put(fields[i], fields[i + 1]); }
            rows.computeIfAbsent(table, ignored -> new ArrayList<>()).add(row);
        }
        void groupMessage(String id, String token, String type, String content) {
            for (String user : List.of("101", "102")) {
                add("db_im_group_inbox_message_0", "message_id", id, "group_id", "201", "chat_id",
                        user.equals("101") ? "311" : "312", "user_id", user, "sender_id", "101",
                        "token", token, "type", type, "content", content);
            }
        }
        DataSource source(String owner) {
            try {
                DataSource source = mock(DataSource.class);
                Connection connection = mock(Connection.class);
                DatabaseMetaData metadata = mock(DatabaseMetaData.class);
                connections.put(owner, connection);
                when(source.getConnection()).thenReturn(connection);
                when(connection.getCatalog()).thenReturn("im_chat_" + owner);
                when(connection.getMetaData()).thenReturn(metadata);
                when(metadata.getURL()).thenReturn("jdbc:mysql://localhost:3306/im_chat_" + owner);
                when(connection.prepareStatement(anyString())).thenAnswer(invocation -> statement(invocation.getArgument(0)));
                return source;
            } catch (Exception exception) { throw new AssertionError(exception); }
        }
        PreparedStatement statement(String sql) throws Exception {
            assertTrue(sql.startsWith("SELECT "), "mutation forbidden: " + sql);
            PreparedStatement statement = mock(PreparedStatement.class);
            Map<Integer, Object> bindings = new LinkedHashMap<>();
            doAnswer(invocation -> { bindings.put(invocation.getArgument(0), invocation.getArgument(1)); return null; })
                    .when(statement).setObject(anyInt(), any());
            doAnswer(invocation -> { bindings.put(invocation.getArgument(0), invocation.getArgument(1)); return null; })
                    .when(statement).setString(anyInt(), anyString());
            when(statement.executeQuery()).thenAnswer(invocation -> {
                String table = sql.split(" FROM ")[1].split(" ")[0];
                List<Object> values = bindings.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
                calls.add(new Call(sql, table, values));
                List<Map<String, String>> matching = rows.getOrDefault(table, List.of()).stream()
                        .filter(row -> ignoreFiltering || matches(sql.substring(sql.indexOf(" WHERE ") + 7), row, values)).toList();
                String[] columns = sql.substring(7, sql.indexOf(" FROM ")).split(",");
                ResultSet result = mock(ResultSet.class);
                int[] cursor = {-1};
                when(result.next()).thenAnswer(ignored -> ++cursor[0] < matching.size());
                when(result.getString(anyInt())).thenAnswer(read -> matching.get(cursor[0]).get(columns[(int) read.getArgument(0) - 1].trim()));
                when(result.getString(anyString())).thenAnswer(read -> matching.get(cursor[0]).get(read.getArgument(0)));
                return result;
            });
            doThrow(new AssertionError("mutation forbidden")).when(statement).executeUpdate();
            return statement;
        }
        boolean matches(String where, Map<String, String> row, List<Object> values) {
            Matcher matcher = Pattern.compile("([a-z_]+) IN \\(([^)]*)\\)|([a-z_]+) LIKE \\?").matcher(where);
            int offset = 0;
            boolean match = false;
            while (matcher.find()) {
                if (matcher.group(3) != null) {
                    String pattern = (String) values.get(offset++);
                    String literal = pattern.substring(0, pattern.length() - 1).replace("\\%", "%").replace("\\_", "_").replace("\\\\", "\\");
                    match |= row.getOrDefault(matcher.group(3), "").startsWith(literal);
                } else {
                    int count = (int) matcher.group(2).chars().filter(character -> character == '?').count();
                    match |= values.subList(offset, offset + count).contains(row.get(matcher.group(1)));
                    offset += count;
                }
            }
            return match;
        }
    }
}
