package com.co.kc.imchat.loadtest.cleanup;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.sql.DataSource;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 只读清理 DTO 恢复器；调用者必须已冻结三个数据所有者，否则跨库读取不是一致快照。
 * 未完成准备的用户保留为 cohortId=null，cohorts 留空；不调用场景清单 parse 校验。
 * 每个查询最多十万行、30 秒，整体最多百万行，journal 最多 16 MiB；超限拒绝而不截断。
 */
final class LoadTestBatchRecovery {
    private static final int MAX_ROWS = 100_000;
    private static final long MAX_JOURNAL_BYTES = 16 * 1024 * 1024;
    private static final long MAX_SNAPSHOT_BYTES = 64 * 1024 * 1024;
    private static final Pattern SECRET = Pattern.compile("authorization|cookie|credential|password|secret|token",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CORRELATION = Pattern.compile(
            "^IMLT1\\|batch=(.{32})\\|round=([0-9]{6})\\|phase=(warmup |measure)"
                    + "\\|sequence=([0-9]{19})\\|padding=(\\.*)$");
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final LoadTestBatchManifest.Schemas SCHEMAS = new LoadTestBatchManifest.Schemas(
            "im_chat_account", "im_chat_social", "im_chat_message");
    private final DataSource account;
    private final DataSource social;
    private final DataSource message;

    LoadTestBatchRecovery(DataSource account, DataSource social, DataSource message) {
        this.account = java.util.Objects.requireNonNull(account);
        this.social = java.util.Objects.requireNonNull(social);
        this.message = java.util.Objects.requireNonNull(message);
    }

    LoadTestBatchManifest recoverOrRead(LoadTestBatchManifest seed, Path journal, Path snapshotPath)
            throws Exception {
        validateSeed(seed);
        byte[] seedBytes = JSON.writeValueAsBytes(seed);
        byte[] journalBytes = boundedBytes(journal, MAX_JOURNAL_BYTES, "journal");
        String seedSha256 = sha256(seedBytes);
        String journalSha256 = sha256(journalBytes);
        Path snapshot = java.util.Objects.requireNonNull(snapshotPath).toAbsolutePath().normalize();
        if (Files.exists(snapshot, LinkOption.NOFOLLOW_LINKS)) {
            return readSnapshot(snapshot, seed, seedSha256, journalSha256);
        }

        LoadTestBatchManifest recovered = recover(seed, journal);
        validateRecoverySnapshot(recovered);
        require(java.util.Arrays.equals(journalBytes, boundedBytes(journal, MAX_JOURNAL_BYTES, "journal")),
                "journal changed during recovery");
        ObjectNode envelope = JSON.createObjectNode();
        envelope.put("schemaVersion", 1);
        envelope.put("batchId", seed.batchId());
        envelope.put("seedSha256", seedSha256);
        envelope.put("journalSha256", journalSha256);
        JsonNode recoveredNode = JSON.valueToTree(recovered);
        envelope.put("recoveredSha256", sha256(JSON.writeValueAsBytes(recoveredNode)));
        envelope.set("recovered", recoveredNode);
        writeSnapshot(snapshot, JSON.writeValueAsBytes(envelope));
        return recovered;
    }

    LoadTestBatchManifest recover(LoadTestBatchManifest seed, Path journal) throws Exception {
        validateSeed(seed);
        Wal wal = readJournal(journal, seed);
        try (Connection a = account.getConnection(); Connection s = social.getConnection();
                Connection m = message.getConnection()) {
            identity(a, SCHEMAS.account());
            identity(s, SCHEMAS.social());
            identity(m, SCHEMAS.message());
            Facts facts = new Facts(seed, wal);
            facts.discover(a, s);
            if (facts.users.isEmpty() && facts.groups.isEmpty()) {
                return facts.alreadyCleaned(a, s, m);
            }
            facts.validateSeedRoots();
            facts.social(s);
            facts.chats(m);
            facts.inbox(m);
            facts.seedReceipts();
            return new LoadTestBatchManifest(1, seed.batchId(), seed.prefix(), SCHEMAS,
                    List.copyOf(facts.users.values()), List.of(), List.copyOf(facts.groups.values()),
                    List.copyOf(facts.relations), List.copyOf(facts.privateChats.values()),
                    List.copyOf(facts.groupChats.values()), List.copyOf(facts.messages.values()), List.copyOf(facts.receipts));
        }
    }

    private static LoadTestBatchManifest readSnapshot(Path path, LoadTestBatchManifest seed,
            String seedSha256, String journalSha256) throws Exception {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "snapshot must be a regular file");
        require(Files.getPosixFilePermissions(path).equals(PosixFilePermissions.fromString("rw-------")),
                "snapshot permissions must be 0600");
        JsonNode envelope = JSON.readTree(boundedBytes(path, MAX_SNAPSHOT_BYTES, "snapshot"));
        exact(envelope, Set.of("schemaVersion", "batchId", "seedSha256", "journalSha256",
                "recoveredSha256", "recovered"));
        JsonNode version = envelope.get("schemaVersion");
        require(version.isIntegralNumber() && version.canConvertToInt() && version.intValue() == 1,
                "snapshot schema version mismatch");
        require(seed.batchId().equals(text(envelope, "batchId")), "snapshot batch mismatch");
        require(seedSha256.equals(digest(envelope, "seedSha256")), "snapshot seed digest mismatch");
        require(journalSha256.equals(digest(envelope, "journalSha256")), "snapshot journal digest mismatch");
        require(envelope.get("recovered") != null && envelope.get("recovered").isObject(),
                "snapshot recovered value must be an object");
        require(digest(envelope, "recoveredSha256").equals(
                        sha256(JSON.writeValueAsBytes(envelope.get("recovered")))),
                "snapshot recovered digest mismatch");
        LoadTestBatchManifest recovered = JSON.treeToValue(envelope.get("recovered"), LoadTestBatchManifest.class);
        validateRecoverySnapshot(recovered);
        require(seed.batchId().equals(recovered.batchId()), "snapshot recovered batch mismatch");
        return recovered;
    }

    private static void writeSnapshot(Path path, byte[] content) throws Exception {
        Path directory = path.getParent();
        require(directory != null && Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS),
                "snapshot directory must exist");
        Path temporary = Files.createTempFile(directory, "." + path.getFileName() + ".", ".tmp",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        boolean moved = false;
        try {
            try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer bytes = ByteBuffer.wrap(content);
                while (bytes.hasRemaining()) {
                    file.write(bytes);
                }
                file.force(true);
            }
            require(!Files.exists(path, LinkOption.NOFOLLOW_LINKS), "snapshot appeared during recovery");
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
            moved = true;
            try (FileChannel parent = FileChannel.open(directory, StandardOpenOption.READ)) {
                parent.force(true);
            }
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void validateSeed(LoadTestBatchManifest seed) {
        require(seed != null && Integer.valueOf(1).equals(seed.version()) && seed.batchId() != null
                && !seed.batchId().isEmpty() && ("loadtest-" + seed.batchId()).equals(seed.prefix())
                && SCHEMAS.equals(seed.schemas()), "invalid seed envelope");
    }

    private static void validateRecoverySnapshot(LoadTestBatchManifest value) {
        validateSeed(value);
        Map<String, LoadTestBatchManifest.User> users = new LinkedHashMap<>();
        Set<String> emails = new HashSet<>();
        for (LoadTestBatchManifest.User user : value.users()) {
            require(user != null && (user.cohortId() == null || !user.cohortId().isEmpty()), "invalid snapshot user");
            prefixed(user.email(), value.prefix() + "-");
            require(emails.add(user.email()), "duplicate snapshot email");
            unique(users, id(user.userId()), user);
        }
        Map<String, LoadTestBatchManifest.Group> groups = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        for (LoadTestBatchManifest.Group group : value.groups()) {
            require(group != null, "null snapshot group");
            String groupId = id(group.groupId());
            String owner = owned(users, group.ownerUserId());
            prefixed(group.name(), value.prefix() + "-");
            require(names.add(group.name()) && group.memberUserIds() != null && !group.memberUserIds().isEmpty(),
                    "invalid snapshot group");
            Set<String> members = new HashSet<>();
            for (String member : group.memberUserIds()) {
                require(members.add(owned(users, member)), "duplicate snapshot group member");
            }
            require(members.contains(owner), "snapshot group excludes owner");
            unique(groups, groupId, group);
        }
        validateSnapshotCohorts(value, users, groups);

        Set<LoadTestBatchManifest.Relation> relations = new HashSet<>();
        for (LoadTestBatchManifest.Relation relation : value.relations()) {
            require(relation != null, "null snapshot relation");
            String user = owned(users, relation.userId());
            String friend = owned(users, relation.friendUserId());
            require(!user.equals(friend) && relations.add(relation), "invalid snapshot relation");
        }
        Map<String, LoadTestBatchManifest.PrivateChat> privateChats = new LinkedHashMap<>();
        Map<String, LoadTestBatchManifest.GroupChat> groupChats = new LinkedHashMap<>();
        Set<String> views = new HashSet<>();
        for (LoadTestBatchManifest.PrivateChat chat : value.privateChats()) {
            require(chat != null, "null snapshot private chat");
            String user = owned(users, chat.userId());
            String peer = owned(users, chat.peerUserId());
            require(!user.equals(peer) && relations.contains(new LoadTestBatchManifest.Relation(user, peer))
                    && views.add("private:" + user + ":" + peer), "invalid snapshot private chat");
            unique(privateChats, id(chat.chatId()), chat);
        }
        for (LoadTestBatchManifest.GroupChat chat : value.groupChats()) {
            require(chat != null, "null snapshot group chat");
            String groupId = owned(groups, chat.groupId());
            String user = owned(users, chat.userId());
            require(groups.get(groupId).memberUserIds().contains(user)
                    && views.add("group:" + groupId + ":" + user), "invalid snapshot group chat");
            String chatId = id(chat.chatId());
            require(!privateChats.containsKey(chatId), "snapshot chat ID overlaps kinds");
            unique(groupChats, chatId, chat);
        }

        Map<String, LoadTestBatchManifest.Message> messages = new LinkedHashMap<>();
        Set<String> tokens = new HashSet<>();
        for (LoadTestBatchManifest.Message message : value.messages()) {
            require(message != null && Set.of("private", "group").contains(message.type()), "invalid snapshot message");
            String sender = owned(users, message.senderUserId());
            boolean tokenOwned;
            if (message.type().equals("private")) {
                require(message.groupId() == null, "private snapshot message has group");
                tokenOwned = message.correlationToken() != null
                        && message.correlationToken().startsWith(value.prefix() + ":");
            } else {
                String groupId = owned(groups, message.groupId());
                require(groups.get(groupId).memberUserIds().contains(sender), "snapshot group sender is not member");
                tokenOwned = message.correlationToken() != null
                        && (message.correlationToken().startsWith(value.prefix() + ":")
                        || (message.correlationToken().equals("system:group_created:" + groupId)
                        && groups.get(groupId).ownerUserId().equals(sender)));
            }
            require(tokenOwned && tokens.add(message.correlationToken()), "invalid snapshot message token");
            unique(messages, id(message.messageId()), message);
        }
        validateSnapshotReceipts(value, users, privateChats, groupChats, messages);
    }

    private static void validateSnapshotCohorts(LoadTestBatchManifest value,
            Map<String, LoadTestBatchManifest.User> users, Map<String, LoadTestBatchManifest.Group> groups) {
        Map<String, LoadTestBatchManifest.Cohort> cohorts = new LinkedHashMap<>();
        Set<String> assigned = new HashSet<>();
        for (LoadTestBatchManifest.Cohort cohort : value.cohorts()) {
            require(cohort != null && cohort.cohortId() != null && !cohort.cohortId().isEmpty()
                    && groups.containsKey(id(cohort.groupId())) && cohort.userIds() != null
                    && !cohort.userIds().isEmpty(), "invalid snapshot cohort");
            unique(cohorts, cohort.cohortId(), cohort);
            Set<String> members = new HashSet<>();
            for (String userId : cohort.userIds()) {
                String owned = owned(users, userId);
                require(members.add(owned) && assigned.add(owned)
                        && cohort.cohortId().equals(users.get(owned).cohortId()), "invalid snapshot cohort user");
            }
            require(members.equals(new HashSet<>(groups.get(cohort.groupId()).memberUserIds())),
                    "snapshot cohort differs from group");
        }
        if (!cohorts.isEmpty()) {
            require(assigned.equals(users.keySet()), "snapshot cohorts omit users");
        }
    }

    private static void validateSnapshotReceipts(LoadTestBatchManifest value,
            Map<String, LoadTestBatchManifest.User> users,
            Map<String, LoadTestBatchManifest.PrivateChat> privateChats,
            Map<String, LoadTestBatchManifest.GroupChat> groupChats,
            Map<String, LoadTestBatchManifest.Message> messages) {
        Set<String> receipts = new HashSet<>();
        for (String receipt : value.receiptIds()) {
            String[] parts = receipt == null ? new String[0] : receipt.split(":", -1);
            require(parts.length == 4 && Set.of("PRIVATE_MESSAGE_SEND", "GROUP_MESSAGE_SEND").contains(parts[0])
                    && receipts.add(receipt), "invalid snapshot receipt");
            String receiver = owned(users, parts[1]);
            String chatId = id(parts[2]);
            LoadTestBatchManifest.Message message = messages.get(id(parts[3]));
            require(message != null && !receiver.equals(message.senderUserId()), "snapshot receipt lacks message");
            if (parts[0].equals("PRIVATE_MESSAGE_SEND")) {
                LoadTestBatchManifest.PrivateChat chat = privateChats.get(chatId);
                require(message.type().equals("private") && chat != null && chat.userId().equals(receiver)
                        && chat.peerUserId().equals(message.senderUserId()), "invalid private snapshot receipt");
            } else {
                LoadTestBatchManifest.GroupChat chat = groupChats.get(chatId);
                require(message.type().equals("group") && chat != null && chat.userId().equals(receiver)
                        && chat.groupId().equals(message.groupId()), "invalid group snapshot receipt");
            }
        }
    }

    private static byte[] boundedBytes(Path path, long maximum, String name) throws Exception {
        require(path != null && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), name + " must be a regular file");
        long size = Files.size(path);
        require(size > 0 && size <= maximum, name + " size is invalid");
        return Files.readAllBytes(path);
    }

    private static String sha256(byte[] value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static String digest(JsonNode envelope, String field) {
        String value = text(envelope, field);
        require(value.matches("[0-9a-f]{64}"), "invalid snapshot digest");
        return value;
    }

    private static void identity(Connection connection, String schema) throws Exception {
        require(schema.equals(connection.getCatalog()), "database catalog mismatch");
        require(connection.getMetaData() != null, "database metadata unavailable");
        String url = connection.getMetaData().getURL();
        require(url != null && url.startsWith("jdbc:mysql://"), "database URL is not MySQL");
        URI uri = URI.create(url.substring(5));
        require(uri.getHost() != null && Set.of("localhost", "127.0.0.1").contains(uri.getHost())
                && uri.getUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
                && ("/" + schema).equals(uri.getRawPath()) && (uri.getPort() == -1 || uri.getPort() > 0),
                "database URL is not exact local owner");
    }

    private static final class Facts {
        final LoadTestBatchManifest seed;
        final Wal wal;
        final Map<String, LoadTestBatchManifest.User> users = new LinkedHashMap<>();
        final Map<String, LoadTestBatchManifest.Group> groups = new LinkedHashMap<>();
        final Set<LoadTestBatchManifest.Relation> relations = new LinkedHashSet<>();
        final Map<String, LoadTestBatchManifest.PrivateChat> privateChats = new LinkedHashMap<>();
        final Map<String, LoadTestBatchManifest.GroupChat> groupChats = new LinkedHashMap<>();
        final Map<String, LoadTestBatchManifest.Message> messages = new LinkedHashMap<>();
        final Map<String, String> payloads = new LinkedHashMap<>();
        final Map<String, String> tokenIds = new LinkedHashMap<>();
        final Map<String, String> privatePeers = new LinkedHashMap<>();
        final Set<String> receipts = new LinkedHashSet<>();
        int rowCount;

        Facts(LoadTestBatchManifest seed, Wal wal) { this.seed = seed; this.wal = wal; }

        void discover(Connection account, Connection social) throws Exception {
            String pattern = escape(seed.prefix()) + "-%";
            Set<String> emails = new HashSet<>();
            for (Map<String, String> row : query(account, "SELECT user_id,email,username FROM db_user WHERE email LIKE ? ESCAPE '\\\\'", List.of(pattern))) {
                String userId = id(row.get("user_id"));
                String email = row.get("email");
                prefixed(email, seed.prefix() + "-");
                prefixed(row.get("username"), seed.prefix() + "-");
                Op signup = wal.signups.get(email);
                if (signup != null) {
                    require(text(signup.ownership, "username").equals(row.get("username")), "signup username differs from WAL");
                    if (signup.result != null) {
                        require(text(signup.result, "userId").equals(userId), "signup ID differs from WAL");
                    }
                }
                require(emails.add(email), "duplicate discovered email");
                unique(users, userId, new LoadTestBatchManifest.User(userId, email, null));
            }
            Set<String> names = new HashSet<>();
            for (Map<String, String> row : query(social, "SELECT group_id,owner_id,name FROM db_im_group WHERE name LIKE ? ESCAPE '\\\\'", List.of(pattern))) {
                String groupId = id(row.get("group_id"));
                String owner = owned(users, row.get("owner_id"));
                String name = row.get("name");
                prefixed(name, seed.prefix() + "-");
                Op operation = wal.groupNames.get(name);
                if (operation != null) {
                    require(text(operation.ownership, "ownerId").equals(owner), "group owner differs from WAL");
                    if (operation.result != null) {
                        require(text(operation.result, "groupId").equals(groupId), "group ID differs from WAL");
                    }
                }
                require(names.add(name), "duplicate discovered group name");
                unique(groups, groupId, new LoadTestBatchManifest.Group(groupId, name, owner, List.of()));
            }
        }

        void validateSeedRoots() {
            Set<String> seen = new HashSet<>();
            for (LoadTestBatchManifest.User user : seed.users()) {
                LoadTestBatchManifest.User actual = users.get(id(user.userId()));
                require(seen.add(user.userId()) && actual != null && actual.email().equals(user.email()), "seed user not proven by DB");
            }
            seen.clear();
            for (LoadTestBatchManifest.Group group : seed.groups()) {
                LoadTestBatchManifest.Group actual = groups.get(id(group.groupId()));
                require(seen.add(group.groupId()) && actual != null && actual.name().equals(group.name())
                        && actual.ownerUserId().equals(group.ownerUserId()), "seed group not proven by DB");
            }
        }

        void social(Connection connection) throws Exception {
            for (Map<String, String> row : intersect(connection, "db_friend", "user_id,friend_user_id",
                    Map.of("user_id", users.keySet(), "friend_user_id", users.keySet()))) {
                LoadTestBatchManifest.Relation relation = new LoadTestBatchManifest.Relation(
                        owned(users, row.get("user_id")), owned(users, row.get("friend_user_id")));
                require(!relation.userId().equals(relation.friendUserId()) && relations.add(relation), "invalid friend edge");
            }
            Map<String, Set<String>> members = new LinkedHashMap<>();
            for (Map<String, String> row : intersect(connection, "db_im_group_member", "group_id,user_id",
                    Map.of("group_id", groups.keySet(), "user_id", users.keySet()))) {
                String group = owned(groups, row.get("group_id"));
                String user = owned(users, row.get("user_id"));
                require(members.computeIfAbsent(group, ignored -> new LinkedHashSet<>()).add(user), "duplicate member");
            }
            // 前缀发现之外仍检查 owner 引用，防止批次用户拥有普通命名的群。
            for (Map<String, String> row : intersect(connection, "db_im_group", "group_id,owner_id,name",
                    Map.of("group_id", groups.keySet(), "owner_id", users.keySet()))) {
                LoadTestBatchManifest.Group group = groups.get(owned(groups, row.get("group_id")));
                require(group.ownerUserId().equals(row.get("owner_id")) && group.name().equals(row.get("name")), "outside owned group");
            }
            groups.replaceAll((key, group) -> {
                Set<String> ids = members.getOrDefault(key, Set.of());
                require(ids.contains(group.ownerUserId()), "group owner missing from members");
                Op op = wal.groupNames.get(group.name());
                if (op != null) {
                    Set<String> intended = new HashSet<>(strings(op.ownership.get("memberIds"), true));
                    intended.add(text(op.ownership, "ownerId"));
                    require(intended.equals(ids), "group membership differs from WAL");
                }
                return new LoadTestBatchManifest.Group(key, group.name(), group.ownerUserId(), ids.stream().sorted().toList());
            });
        }

        void chats(Connection connection) throws Exception {
            Set<String> seedChatIds = new LinkedHashSet<>();
            seed.privateChats().forEach(chat -> seedChatIds.add(id(chat.chatId())));
            seed.groupChats().forEach(chat -> seedChatIds.add(id(chat.chatId())));
            Set<String> views = new HashSet<>();
            for (int shard = 0; shard < 8; shard++) {
                for (Map<String, String> row : intersect(connection, "db_im_private_chat_" + shard, "chat_id,user_id,peer_user_id",
                        Map.of("user_id", users.keySet(), "peer_user_id", users.keySet(), "chat_id", seedChatIds))) {
                    String user = owned(users, row.get("user_id"));
                    String peer = owned(users, row.get("peer_user_id"));
                    String chat = id(row.get("chat_id"));
                    require(relations.contains(new LoadTestBatchManifest.Relation(user, peer)), "private chat missing owned edge");
                    require(views.add("private:" + user + ":" + peer), "duplicate private view");
                    unique(privateChats, chat, new LoadTestBatchManifest.PrivateChat(chat, user, peer));
                }
                for (Map<String, String> row : intersect(connection, "db_im_group_chat_" + shard, "chat_id,group_id,user_id",
                        Map.of("user_id", users.keySet(), "group_id", groups.keySet(), "chat_id", seedChatIds))) {
                    String user = owned(users, row.get("user_id"));
                    String group = owned(groups, row.get("group_id"));
                    String chat = id(row.get("chat_id"));
                    require(groups.get(group).memberUserIds().contains(user), "chat user not group member");
                    require(!privateChats.containsKey(chat) && views.add("group:" + group + ":" + user), "duplicate chat view");
                    unique(groupChats, chat, new LoadTestBatchManifest.GroupChat(chat, group, user));
                }
            }
            require(java.util.Collections.disjoint(privateChats.keySet(), groupChats.keySet()), "chat ID overlaps kinds");
            for (LoadTestBatchManifest.PrivateChat chat : seed.privateChats()) {
                require(chat.equals(privateChats.get(chat.chatId())), "seed private chat not proven");
            }
            for (LoadTestBatchManifest.GroupChat chat : seed.groupChats()) {
                require(chat.equals(groupChats.get(chat.chatId())), "seed group chat not proven");
            }
        }

        void inbox(Connection connection) throws Exception {
            Set<String> allChats = new LinkedHashSet<>(privateChats.keySet());
            allChats.addAll(groupChats.keySet());
            Set<String> seedMessages = new LinkedHashSet<>();
            Set<String> seedTokens = new LinkedHashSet<>();
            seed.messages().forEach(value -> { seedMessages.add(id(value.messageId())); seedTokens.add(value.correlationToken()); });
            Set<String> copies = new HashSet<>();
            for (String kind : List.of("private", "group")) {
                Map<String, Set<String>> predicates = new LinkedHashMap<>();
                predicates.put("user_id", users.keySet()); predicates.put("sender_id", users.keySet());
                predicates.put("chat_id", allChats); predicates.put("message_id", seedMessages); predicates.put("token", seedTokens);
                if (kind.equals("group")) { predicates.put("group_id", groups.keySet()); }
                for (int shard = 0; shard < 8; shard++) {
                    String columns = "message_id,chat_id,user_id,sender_id,token,type,content" + (kind.equals("group") ? ",group_id" : "");
                    for (Map<String, String> row : intersect(connection, "db_im_" + kind + "_inbox_message_" + shard, columns, predicates)) {
                        String receiver = owned(users, row.get("user_id"));
                        String sender = owned(users, row.get("sender_id"));
                        String chat = id(row.get("chat_id"));
                        String message = id(row.get("message_id"));
                        String group = null;
                        if (kind.equals("private")) {
                            LoadTestBatchManifest.PrivateChat fact = privateChats.get(chat);
                            require(fact != null && receiver.equals(fact.userId())
                                    && (sender.equals(receiver) || sender.equals(fact.peerUserId())), "outside private inbox");
                            String peer = sender.equals(receiver) ? fact.peerUserId() : receiver;
                            String previousPeer = privatePeers.putIfAbsent(message, peer);
                            require(previousPeer == null || previousPeer.equals(peer), "private message copies disagree on peer");
                        } else {
                            group = owned(groups, row.get("group_id"));
                            LoadTestBatchManifest.GroupChat fact = groupChats.get(chat);
                            require(fact != null && receiver.equals(fact.userId()) && group.equals(fact.groupId())
                                    && groups.get(group).memberUserIds().contains(sender), "outside group inbox");
                        }
                        String token = row.get("token");
                        String content = row.get("content");
                        boolean system = group != null && ("system:group_created:" + group).equals(token)
                                && "7".equals(row.get("type")) && groups.get(group).ownerUserId().equals(sender)
                                && "群聊已创建".equals(content);
                        boolean batch = token != null && token.startsWith(seed.prefix() + ":") && token.length() > seed.prefix().length() + 1
                                && correlation(content, seed.batchId())
                                && Set.of("1", "2", "3", "4", "5", "6").contains(row.get("type"));
                        require(system || batch, "nonbatch inbox token/content/type");
                        LoadTestBatchManifest.Message value = new LoadTestBatchManifest.Message(message, kind, sender, group, token);
                        LoadTestBatchManifest.Message previous = messages.putIfAbsent(message, value);
                        require(previous == null || previous.equals(value), "message copies disagree");
                        String payload = row.get("type") + ":" + content;
                        String priorPayload = payloads.putIfAbsent(message, payload);
                        require(priorPayload == null || priorPayload.equals(payload), "message payload copies disagree");
                        String priorId = tokenIds.putIfAbsent(token, message);
                        require(priorId == null || priorId.equals(message), "token reused by another message");
                        require(copies.add(message + ":" + chat + ":" + receiver), "duplicate inbox copy");
                        if (!receiver.equals(sender)) {
                            receipts.add((kind.equals("private") ? "PRIVATE" : "GROUP") + "_MESSAGE_SEND:" + receiver + ":" + chat + ":" + message);
                        }
                    }
                }
            }
            for (LoadTestBatchManifest.Message value : seed.messages()) {
                require(value.equals(messages.get(value.messageId())), "seed message not proven by DB");
            }
        }

        void seedReceipts() {
            for (String receipt : seed.receiptIds()) {
                String[] parts = receipt.split(":", -1);
                require(parts.length == 4 && Set.of("PRIVATE_MESSAGE_SEND", "GROUP_MESSAGE_SEND").contains(parts[0]), "invalid seed receipt");
                String receiver = owned(users, parts[1]);
                String chat = id(parts[2]);
                LoadTestBatchManifest.Message value = messages.get(id(parts[3]));
                require(value != null && !receiver.equals(value.senderUserId()), "receipt has no durable message");
                if (parts[0].equals("PRIVATE_MESSAGE_SEND")) {
                    LoadTestBatchManifest.PrivateChat fact = privateChats.get(chat);
                    require(value.type().equals("private") && fact != null && fact.userId().equals(receiver)
                            && fact.peerUserId().equals(value.senderUserId()), "receipt has no durable private chat");
                } else {
                    LoadTestBatchManifest.GroupChat fact = groupChats.get(chat);
                    require(value.type().equals("group") && fact != null && fact.userId().equals(receiver)
                            && fact.groupId().equals(value.groupId()), "receipt has no durable group chat");
                }
                receipts.add(receipt);
            }
        }

        LoadTestBatchManifest alreadyCleaned(Connection a, Connection s, Connection m) throws Exception {
            // 无 DB 根时只接受完整历史清单，不把未提交/缺失 ID 的 intent 当作普通 ID 的删除授权。
            ObjectNode tree = JSON.valueToTree(seed);
            tree.withArray("messages").forEach(node -> { if ("private".equals(node.path("type").asText())) { ((ObjectNode) node).remove("groupId"); } });
            LoadTestBatchManifest.parse(JSON.writeValueAsString(tree));
            Set<String> ids = new LinkedHashSet<>();
            Set<String> emails = new LinkedHashSet<>();
            for (LoadTestBatchManifest.User user : seed.users()) {
                ids.add(id(user.userId())); emails.add(user.email());
                require(user.email().equals(wal.confirmed.get(user.userId())), "cleaned seed user not WAL confirmed");
            }
            if (ids.isEmpty()) {
                require(wal.confirmed.isEmpty() && wal.groupNames.isEmpty()
                                && wal.operations.values().stream().allMatch(operation ->
                                operation.operation.equals("signUp") && !operation.committed),
                        "empty cleaned seed has committed or non-signup WAL facts");
                return seed;
            }
            require(!ids.isEmpty() && ids.equals(wal.confirmed.keySet()) && wal.signups.size() == ids.size(), "cleaned seed incomplete WAL snapshot");
            Set<String> groupIds = new LinkedHashSet<>();
            for (LoadTestBatchManifest.Group group : seed.groups()) {
                groupIds.add(id(group.groupId()));
                Op op = wal.groupNames.get(group.name());
                require(op != null && text(op.ownership, "ownerId").equals(group.ownerUserId()), "cleaned group not in WAL");
                Set<String> members = new HashSet<>(strings(op.ownership.get("memberIds"), true));
                members.add(group.ownerUserId());
                require(members.equals(new HashSet<>(group.memberUserIds()))
                        && (op.result == null || group.groupId().equals(text(op.result, "groupId"))), "cleaned group differs from WAL");
            }
            require(seed.groups().size() == wal.groupNames.size(), "cleaned seed missing WAL groups");
            Set<String> chats = new LinkedHashSet<>();
            seed.privateChats().forEach(chat -> chats.add(chat.chatId())); seed.groupChats().forEach(chat -> chats.add(chat.chatId()));
            Set<String> messages = new LinkedHashSet<>(); Set<String> tokens = new LinkedHashSet<>();
            seed.messages().forEach(value -> { messages.add(value.messageId()); tokens.add(value.correlationToken()); });
            absent(intersect(a, "db_user", "user_id,email", Map.of("user_id", ids, "email", emails)));
            absent(intersect(s, "db_friend", "user_id", Map.of("user_id", ids, "friend_user_id", ids)));
            absent(intersect(s, "db_im_group_member", "group_id", Map.of("user_id", ids, "group_id", groupIds)));
            absent(intersect(s, "db_im_group", "group_id", Map.of("owner_id", ids, "group_id", groupIds)));
            for (int shard = 0; shard < 8; shard++) {
                absent(intersect(m, "db_im_private_chat_" + shard, "chat_id", Map.of("user_id", ids, "peer_user_id", ids, "chat_id", chats)));
                absent(intersect(m, "db_im_group_chat_" + shard, "chat_id", Map.of("user_id", ids, "group_id", groupIds, "chat_id", chats)));
                Map<String, Set<String>> predicates = new LinkedHashMap<>(Map.of("user_id", ids, "sender_id", ids, "chat_id", chats,
                        "message_id", messages, "token", tokens));
                absent(intersect(m, "db_im_private_inbox_message_" + shard, "message_id", predicates));
                predicates.put("group_id", groupIds);
                absent(intersect(m, "db_im_group_inbox_message_" + shard, "message_id", predicates));
            }
            return seed;
        }

        List<Map<String, String>> intersect(Connection connection, String table, String columns,
                Map<String, Set<String>> predicates) throws Exception {
            List<String> clauses = new ArrayList<>();
            List<Object> parameters = new ArrayList<>();
            predicates.forEach((column, values) -> {
                if (!values.isEmpty()) {
                    require(values.size() <= MAX_ROWS, "too many recovery parameters");
                    clauses.add(column + " IN (" + String.join(",", java.util.Collections.nCopies(values.size(), "?")) + ")");
                    parameters.addAll(values);
                }
            });
            return query(connection, "SELECT " + columns + " FROM " + table + " WHERE "
                    + (clauses.isEmpty() ? "1=0" : String.join(" OR ", clauses)), parameters);
        }

        List<Map<String, String>> query(Connection connection, String sql, List<?> parameters) throws Exception {
            require(!Thread.currentThread().isInterrupted(), "recovery interrupted");
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                statement.setMaxRows(MAX_ROWS + 1);
                for (int index = 0; index < parameters.size(); index++) { statement.setObject(index + 1, parameters.get(index)); }
                try (ResultSet rows = statement.executeQuery()) {
                    List<Map<String, String>> result = new ArrayList<>();
                    String[] columns = sql.substring(7, sql.indexOf(" FROM ")).split(",");
                    while (rows.next()) {
                        require(result.size() < MAX_ROWS && ++rowCount <= 1_000_000, "recovery row limit exceeded");
                        require(!Thread.currentThread().isInterrupted(), "recovery interrupted");
                        Map<String, String> row = new LinkedHashMap<>();
                        for (int index = 0; index < columns.length; index++) { row.put(columns[index], rows.getString(index + 1)); }
                        result.add(row);
                    }
                    return result;
                }
            }
        }
    }

    private static Wal readJournal(Path path, LoadTestBatchManifest seed) throws Exception {
        require(path != null && Files.isRegularFile(path) && Files.size(path) > 0 && Files.size(path) <= MAX_JOURNAL_BYTES,
                "existing bounded journal required");
        Wal wal = new Wal();
        try (java.io.BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                JsonNode record = JSON.readTree(line);
                require(record != null && record.isObject(), "invalid WAL record");
                String type = text(record, "type");
                Set<String> fields = new HashSet<>(Set.of("schemaVersion", "batchId", "operationId", "type", "operation"));
                switch (type) {
                    case "intent" -> fields.add("ownership");
                    case "invoked" -> fields.add("attempt");
                    case "reconciled" -> fields.addAll(Set.of("attempt", "outcome", "result", "evidence"));
                    case "completed" -> fields.addAll(Set.of("attempt", "result"));
                    case "checkpoint" -> fields.addAll(Set.of("checkpointId", "completedOperationIds"));
                    default -> throw new IllegalStateException("unknown WAL type");
                }
                exact(record, fields);
                require(record.get("schemaVersion").isIntegralNumber() && record.get("schemaVersion").canConvertToLong()
                        && record.get("schemaVersion").asLong() == 1
                        && seed.batchId().equals(text(record, "batchId")), "WAL version/batch mismatch");
                String operationId = text(record, "operationId");
                require(operationId.length() <= 128 && operationId.matches("[A-Za-z0-9][A-Za-z0-9._:-]*")
                        && !SECRET.matcher(operationId).find(), "unsafe WAL operationId");
                String operation = text(record, "operation");
                if (type.equals("checkpoint")) {
                    require(operation.equals("checkpoint"), "invalid checkpoint operation");
                    String checkpointId = text(record, "checkpointId");
                    List<String> completed = strings(record.get("completedOperationIds"), false);
                    for (String key : completed) {
                        require(wal.operations.containsKey(key) && wal.operations.get(key).last.equals("completed"), "checkpoint references incomplete operation");
                    }
                    JsonNode previous = wal.checkpoints.putIfAbsent(checkpointId, record);
                    require(previous == null || previous.equals(record), "conflicting checkpoint");
                    continue;
                }
                require(Set.of("signUp", "addFriend", "createGroup").contains(operation), "unknown WAL operation");
                if (type.equals("intent")) {
                    Op prior = wal.operations.get(operationId);
                    if (prior != null) { require(prior.intent.equals(record), "conflicting WAL intent"); continue; }
                    JsonNode ownership = record.get("ownership");
                    ownership(operation, ownership, seed.prefix() + "-", wal.established.keySet());
                    Op op = new Op(record, operation, ownership);
                    wal.operations.put(operationId, op);
                    if (operation.equals("signUp")) {
                        require(wal.signups.putIfAbsent(text(ownership, "email"), op) == null, "duplicate signup email intent");
                    } else if (operation.equals("createGroup")) {
                        require(wal.groupNames.putIfAbsent(text(ownership, "groupName"), op) == null, "duplicate group intent");
                    }
                    continue;
                }
                Op op = wal.operations.get(operationId);
                require(op != null && op.operation.equals(operation), "WAL transition without matching intent");
                JsonNode attemptNode = record.get("attempt");
                require(attemptNode.isIntegralNumber() && attemptNode.canConvertToInt() && attemptNode.asInt() > 0, "invalid WAL attempt");
                int attempt = attemptNode.asInt();
                if (type.equals("reconciled")) {
                    String outcome = text(record, "outcome");
                    require(Set.of("committed", "not_committed").contains(outcome), "invalid WAL outcome");
                    safe(record.get("evidence"));
                    if (outcome.equals("not_committed")) { require(record.get("result").isNull(), "not_committed result must be null"); }
                    else { result(op, record.get("result")); }
                } else if (type.equals("completed")) { result(op, record.get("result")); }
                String transition = type + ":" + attempt;
                JsonNode previous = op.transitions.get(transition);
                if (previous != null) { require(previous.equals(record), "conflicting WAL transition"); continue; }
                switch (type) {
                    case "invoked" -> require((op.last.equals("intent") && attempt == 1)
                            || (op.last.equals("reconciled") && !op.committed && (long) attempt == (long) op.attempt + 1), "invalid WAL invocation");
                    case "reconciled" -> {
                        require(op.last.equals("invoked") && attempt == op.attempt, "invalid WAL reconciliation");
                        op.committed = text(record, "outcome").equals("committed");
                        op.result = op.committed ? record.get("result") : null;
                        if (op.committed && operation.equals("signUp")) {
                            String userId = text(op.result, "userId");
                            String email = text(op.ownership, "email");
                            String prior = wal.confirmed.putIfAbsent(userId, email);
                            require(prior == null || prior.equals(email), "WAL signup ID conflict");
                        }
                    }
                    case "completed" -> {
                        require(op.last.equals("reconciled") && op.committed && attempt == op.attempt
                                && record.get("result").equals(op.result), "completion requires identical committed result");
                        if (operation.equals("signUp")) {
                            require(wal.established.putIfAbsent(text(op.result, "userId"), operationId) == null, "duplicate established user");
                        }
                    }
                    default -> throw new IllegalStateException("invalid WAL transition");
                }
                op.transitions.put(transition, record); op.last = type; op.attempt = attempt;
            }
        }
        require(!wal.operations.isEmpty() && !wal.signups.isEmpty(), "journal has no batch roots");
        return wal;
    }

    private static void ownership(String operation, JsonNode value, String prefix, Set<String> established) {
        safe(value);
        if (operation.equals("signUp")) {
            exact(value, Set.of("email", "username")); prefixed(text(value, "email"), prefix); prefixed(text(value, "username"), prefix);
        } else if (operation.equals("addFriend")) {
            exact(value, Set.of("userId", "friendUserId"));
            String user = id(text(value, "userId")); String friend = id(text(value, "friendUserId"));
            require(!user.equals(friend) && established.containsAll(List.of(user, friend)), "friend intent references unconfirmed users");
        } else {
            exact(value, Set.of("ownerId", "memberIds", "groupName"));
            String owner = id(text(value, "ownerId")); List<String> members = strings(value.get("memberIds"), true);
            prefixed(text(value, "groupName"), prefix);
            require(!members.contains(owner) && established.contains(owner) && established.containsAll(members), "group intent references unconfirmed users");
        }
    }

    private static void result(Op op, JsonNode value) {
        if (op.operation.equals("signUp")) { exact(value, Set.of("userId")); id(text(value, "userId")); }
        else if (op.operation.equals("addFriend")) {
            exact(value, Set.of("userId", "friendUserId"));
            for (String field : List.of("userId", "friendUserId")) {
                require(id(text(value, field)).equals(text(op.ownership, field)), "friend result differs from intent");
            }
        } else {
            // Social commits the group before Message-derived chats and receipts converge.
            if (value != null && value.isObject() && value.size() == 1 && value.has("groupId")) {
                exact(value, Set.of("groupId"));
                id(text(value, "groupId"));
                return;
            }
            exact(value, Set.of("groupId", "ownerChatId", "memberChats"));
            id(text(value, "groupId")); Set<String> chats = new HashSet<>(Set.of(id(text(value, "ownerChatId"))));
            Set<String> users = new HashSet<>(); JsonNode members = value.get("memberChats");
            require(members.isArray() && !members.isEmpty(), "invalid group memberChats");
            for (JsonNode member : members) {
                exact(member, Set.of("userId", "chatId"));
                require(users.add(id(text(member, "userId"))) && chats.add(id(text(member, "chatId"))), "duplicate WAL member/chat");
            }
            require(users.equals(new HashSet<>(strings(op.ownership.get("memberIds"), true))), "group result differs from intent");
        }
    }

    private static final class Wal {
        final Map<String, Op> operations = new LinkedHashMap<>();
        final Map<String, Op> signups = new LinkedHashMap<>();
        final Map<String, Op> groupNames = new LinkedHashMap<>();
        final Map<String, String> confirmed = new LinkedHashMap<>();
        final Map<String, String> established = new LinkedHashMap<>();
        final Map<String, JsonNode> checkpoints = new LinkedHashMap<>();
    }

    private static final class Op {
        final JsonNode intent;
        final String operation;
        final JsonNode ownership;
        final Map<String, JsonNode> transitions = new LinkedHashMap<>();
        String last = "intent";
        int attempt;
        boolean committed;
        JsonNode result;
        Op(JsonNode intent, String operation, JsonNode ownership) { this.intent = intent; this.operation = operation; this.ownership = ownership; }
    }

    private static void exact(JsonNode node, Set<String> expected) {
        require(node != null && node.isObject(), "WAL payload must be object");
        Set<String> fields = new HashSet<>(); node.fieldNames().forEachRemaining(fields::add);
        require(fields.equals(expected), "WAL fields differ from schema");
    }
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        require(value != null && value.isTextual() && !value.textValue().isEmpty(), "WAL field must be nonempty string");
        return value.textValue();
    }
    private static List<String> strings(JsonNode node, boolean ids) {
        require(node != null && node.isArray() && !node.isEmpty(), "WAL array must be nonempty");
        List<String> result = new ArrayList<>(); Set<String> unique = new HashSet<>();
        for (JsonNode value : node) {
            require(value.isTextual() && !value.textValue().isEmpty(), "WAL array element must be string");
            String item = ids ? id(value.textValue()) : value.textValue();
            require(unique.add(item), "duplicate WAL array element"); result.add(item);
        }
        return result;
    }
    private static void safe(JsonNode node) {
        require(node != null, "missing WAL JSON value");
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> { require(!SECRET.matcher(entry.getKey()).find(), "credential-like WAL field"); safe(entry.getValue()); });
        } else if (node.isArray()) { node.forEach(LoadTestBatchRecovery::safe); }
    }
    private static String id(String value) {
        require(value != null && value.matches("[1-9][0-9]*"), "ID must be positive canonical Long string");
        try { Long.parseLong(value); } catch (NumberFormatException exception) { throw new IllegalStateException("ID exceeds Long", exception); }
        return value;
    }
    private static String owned(Map<String, ?> facts, String value) {
        require(facts.containsKey(id(value)), "outside database ownership"); return value;
    }
    private static <T> void unique(Map<String, T> facts, String id, T value) {
        require(facts.putIfAbsent(id, value) == null, "duplicate DB fact ID");
    }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }
    private static boolean correlation(String content, String batchId) {
        if (content == null || content.getBytes(StandardCharsets.UTF_8).length != 256
                || batchId.length() > 32 || !batchId.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            return false;
        }
        java.util.regex.Matcher matcher = CORRELATION.matcher(content);
        return matcher.matches() && matcher.group(1).equals(batchId + " ".repeat(32 - batchId.length()));
    }
    private static void prefixed(String value, String prefix) { require(value != null && value.startsWith(prefix) && value.length() > prefix.length(), "outside prefix boundary"); }
    private static void absent(List<?> rows) { require(rows.isEmpty(), "roots absent but durable rows remain"); }
    private static void require(boolean valid, String message) { if (!valid) { throw new IllegalStateException(message); } }
}
