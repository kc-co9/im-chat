package com.co.kc.imchat.loadtest.cleanup;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

record LoadTestBatchManifest(
        Integer version,
        String batchId,
        String prefix,
        Schemas schemas,
        List<User> users,
        List<Cohort> cohorts,
        List<Group> groups,
        List<Relation> relations,
        List<PrivateChat> privateChats,
        List<GroupChat> groupChats,
        List<Message> messages,
        List<String> receiptIds) {

    private static final long SCHEMA_VERSION = 1L;
    private static final String ACCOUNT_SCHEMA = "im_chat_account";
    private static final String SOCIAL_SCHEMA = "im_chat_social";
    private static final String MESSAGE_SCHEMA = "im_chat_message";
    private static final Set<String> RECEIPT_TYPES = Set.of("PRIVATE_MESSAGE_SEND", "GROUP_MESSAGE_SEND");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build())
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    LoadTestBatchManifest {
        users = immutable(users, "users");
        cohorts = immutable(cohorts, "cohorts");
        groups = immutable(groups, "groups");
        relations = immutable(relations, "relations");
        privateChats = immutable(privateChats, "privateChats");
        groupChats = immutable(groupChats, "groupChats");
        messages = immutable(messages, "messages");
        receiptIds = immutable(receiptIds, "receiptIds");
    }

    static LoadTestBatchManifest read(Path path) {
        try {
            return parse(Files.readString(path));
        } catch (IOException exception) {
            throw new IllegalArgumentException("unable to read manifest", exception);
        }
    }

    static LoadTestBatchManifest parse(String json) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            validateShape(root);
            validateTypes(root);
            LoadTestBatchManifest manifest = OBJECT_MAPPER.treeToValue(root, LoadTestBatchManifest.class);
            manifest.validate();
            return manifest;
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid load-test manifest", exception);
        }
    }

    private void validate() {
        require(version != null && version == SCHEMA_VERSION, "unsupported manifest version");
        nonEmpty(batchId, "batchId");
        require(("loadtest-" + batchId).equals(prefix), "manifest prefix does not match batch");
        require(schemas != null
                        && ACCOUNT_SCHEMA.equals(schemas.account())
                        && SOCIAL_SCHEMA.equals(schemas.social())
                        && MESSAGE_SCHEMA.equals(schemas.message()),
                "manifest schemas do not match owners");

        Map<String, User> usersById = uniqueMap(users, User::userId, "userId");
        Set<String> emails = new HashSet<>();
        for (User user : users) {
            canonicalId(user.userId(), "userId");
            nonEmpty(user.email(), "email");
            nonEmpty(user.cohortId(), "cohortId");
            require(user.email().startsWith(prefix + "-"), "user email is outside batch prefix boundary");
            require(emails.add(user.email()), "duplicate user email");
        }

        Map<String, Cohort> cohortsById = uniqueMap(cohorts, Cohort::cohortId, "cohortId");
        Set<String> cohortGroupIds = new HashSet<>();
        Set<String> cohortUsers = new HashSet<>();
        for (Cohort cohort : cohorts) {
            nonEmpty(cohort.cohortId(), "cohortId");
            canonicalId(cohort.groupId(), "cohort groupId");
            ownedIds(cohort.userIds(), usersById.keySet(), "cohort userIds");
            require(cohortGroupIds.add(cohort.groupId()), "groupId belongs to multiple cohorts");
            for (String userId : cohort.userIds()) {
                require(usersById.get(userId).cohortId().equals(cohort.cohortId()), "user cohort mismatch");
                require(cohortUsers.add(userId), "user belongs to multiple cohorts");
            }
        }
        require(cohortUsers.equals(usersById.keySet()), "every user must belong to one cohort");

        Map<String, Group> groupsById = uniqueMap(groups, Group::groupId, "groupId");
        for (Group group : groups) {
            canonicalId(group.groupId(), "groupId");
            canonicalId(group.ownerUserId(), "group ownerUserId");
            nonEmpty(group.name(), "group name");
            require(group.name().startsWith(prefix + "-"), "group name is outside batch prefix boundary");
            require(usersById.containsKey(group.ownerUserId()), "group owner is not owned");
            ownedIds(group.memberUserIds(), usersById.keySet(), "group memberUserIds");
            require(group.memberUserIds().contains(group.ownerUserId()), "group members exclude owner");
            Cohort cohort = cohorts.stream().filter(value -> value.groupId().equals(group.groupId())).findFirst().orElse(null);
            require(cohort != null && sameSet(cohort.userIds(), group.memberUserIds()),
                    "group members do not match cohort");
        }
        require(groupsById.keySet().equals(cohortGroupIds), "groups do not match cohorts");

        Set<Relation> relationSet = new HashSet<>();
        for (Relation relation : relations) {
            canonicalId(relation.userId(), "relation userId");
            canonicalId(relation.friendUserId(), "relation friendUserId");
            require(usersById.containsKey(relation.userId()) && usersById.containsKey(relation.friendUserId()),
                    "relation references an unowned user");
            require(relationSet.add(relation), "duplicate relation");
        }

        Map<String, ChatFact> chats = new HashMap<>();
        Set<String> views = new HashSet<>();
        for (PrivateChat chat : privateChats) {
            canonicalId(chat.chatId(), "private chatId");
            canonicalId(chat.userId(), "private chat userId");
            canonicalId(chat.peerUserId(), "private chat peerUserId");
            require(usersById.containsKey(chat.userId()) && usersById.containsKey(chat.peerUserId()),
                    "private chat references an unowned user");
            require(!chat.userId().equals(chat.peerUserId())
                    && relationSet.contains(new Relation(chat.userId(), chat.peerUserId())),
                    "private chat requires an owned relation");
            addChat(chats, views, chat.chatId(), new ChatFact("private", chat.userId(), chat.peerUserId(), null),
                    "private:" + chat.userId() + ":" + chat.peerUserId());
        }
        for (GroupChat chat : groupChats) {
            canonicalId(chat.chatId(), "group chatId");
            canonicalId(chat.groupId(), "group chat groupId");
            canonicalId(chat.userId(), "group chat userId");
            Group group = groupsById.get(chat.groupId());
            require(group != null && group.memberUserIds().contains(chat.userId()),
                    "group chat user is not an owned member");
            addChat(chats, views, chat.chatId(), new ChatFact("group", chat.userId(), null, chat.groupId()),
                    "group:" + chat.groupId() + ":" + chat.userId());
        }
        for (Relation relation : relations) {
            require(views.contains("private:" + relation.userId() + ":" + relation.friendUserId()),
                    "relation is missing private chat view");
        }
        for (Group group : groups) {
            for (String userId : group.memberUserIds()) {
                require(views.contains("group:" + group.groupId() + ":" + userId),
                        "group member is missing group chat view");
            }
        }

        Map<String, Message> messagesById = uniqueMap(messages, Message::messageId, "messageId");
        Set<String> tokens = new HashSet<>();
        for (Message message : messages) {
            canonicalId(message.messageId(), "messageId");
            canonicalId(message.senderUserId(), "message senderUserId");
            require(usersById.containsKey(message.senderUserId()), "message sender is not owned");
            require(Set.of("private", "group").contains(message.type()), "unknown message type");
            if (message.type().equals("group")) {
                canonicalId(message.groupId(), "message groupId");
                Group group = groupsById.get(message.groupId());
                require(group != null && group.memberUserIds().contains(message.senderUserId()),
                        "group message sender is not a member");
            } else {
                require(message.groupId() == null, "private message must not contain groupId");
            }
            boolean ownedSystemToken = "group".equals(message.type())
                    && ("system:group_created:" + message.groupId()).equals(message.correlationToken())
                    && groupsById.get(message.groupId()).ownerUserId().equals(message.senderUserId());
            require(message.correlationToken() != null
                            && (message.correlationToken().startsWith(prefix + ":") || ownedSystemToken),
                    "message token is outside batch prefix boundary");
            require(tokens.add(message.correlationToken()), "duplicate message token");
        }
        validateReceipts(usersById, chats, messagesById);
        require(cohortsById.size() == cohorts.size(), "duplicate cohortId");
    }

    private void validateReceipts(Map<String, User> usersById, Map<String, ChatFact> chats,
            Map<String, Message> messagesById) {
        Set<String> seen = new HashSet<>();
        for (String receiptId : receiptIds) {
            String[] parts = receiptId == null ? new String[0] : receiptId.split(":", -1);
            require(parts.length == 4 && RECEIPT_TYPES.contains(parts[0]), "invalid receiptId type");
            canonicalId(parts[1], "receipt receiver");
            canonicalId(parts[2], "receipt chatId");
            canonicalId(parts[3], "receipt messageId");
            require(usersById.containsKey(parts[1]), "receipt receiver is not owned");
            String type = parts[0].equals("PRIVATE_MESSAGE_SEND") ? "private" : "group";
            ChatFact chat = chats.get(parts[2]);
            Message message = messagesById.get(parts[3]);
            require(chat != null && chat.type().equals(type) && chat.userId().equals(parts[1]),
                    "receipt chat does not match receiver and type");
            require(message != null && message.type().equals(type), "receipt message type mismatch");
            require(type.equals("private") ? chat.peerUserId().equals(message.senderUserId())
                    : chat.groupId().equals(message.groupId()), "receipt chat does not match message");
            require(seen.add(receiptId), "duplicate receiptId");
        }
    }

    private static void validateShape(JsonNode root) {
        exactFields(root, Set.of("version", "batchId", "prefix", "schemas", "users", "cohorts", "groups",
                "relations", "privateChats", "groupChats", "messages", "receiptIds"), "manifest");
        exactFields(root.required("schemas"), Set.of("account", "social", "message"), "schemas");
        each(root, "users", node -> exactFields(node, Set.of("userId", "email", "cohortId"), "user"));
        each(root, "cohorts", node -> exactFields(node, Set.of("cohortId", "userIds", "groupId"), "cohort"));
        each(root, "groups", node -> exactFields(node,
                Set.of("groupId", "name", "ownerUserId", "memberUserIds"), "group"));
        each(root, "relations", node -> exactFields(node, Set.of("userId", "friendUserId"), "relation"));
        each(root, "privateChats", node -> exactFields(node,
                Set.of("chatId", "userId", "peerUserId"), "private chat"));
        each(root, "groupChats", node -> exactFields(node, Set.of("chatId", "groupId", "userId"), "group chat"));
        each(root, "messages", node -> {
            require(node.has("type") && node.get("type").isTextual(), "message type must be a string");
            Set<String> fields = "group".equals(node.get("type").textValue())
                    ? Set.of("messageId", "type", "senderUserId", "groupId", "correlationToken")
                    : Set.of("messageId", "type", "senderUserId", "correlationToken");
            exactFields(node, fields, "message");
        });
    }

    private static void each(JsonNode root, String field, java.util.function.Consumer<JsonNode> validator) {
        JsonNode array = root.required(field);
        require(array.isArray(), field + " must be an array");
        array.forEach(validator);
    }

    private static void validateTypes(JsonNode node) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                require(!value.isNull(), "null manifest value");
                if (value.isContainerNode()) {
                    validateTypes(value);
                } else if ("version".equals(entry.getKey())) {
                    require(value.isIntegralNumber(), "version must be an integer");
                } else {
                    require(value.isTextual(), "manifest value must be a string");
                }
            });
        } else if (node.isArray()) {
            node.forEach(value -> {
                require(value.isObject() || value.isTextual(), "invalid manifest array value");
                validateTypes(value);
            });
        }
    }

    private static void exactFields(JsonNode node, Set<String> expected, String name) {
        require(node != null && node.isObject(), name + " must be an object");
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(expected), name + " fields do not match schema");
    }

    private static <T> Map<String, T> uniqueMap(List<T> values, Function<T, String> key, String name) {
        Map<String, T> result = new HashMap<>();
        for (T value : values) {
            String id = key.apply(value);
            require(result.putIfAbsent(id, value) == null, "duplicate " + name);
        }
        return result;
    }

    private static void addChat(Map<String, ChatFact> chats, Set<String> views, String id, ChatFact fact,
            String view) {
        require(chats.putIfAbsent(id, fact) == null, "duplicate chatId");
        require(views.add(view), "duplicate chat view");
    }

    private static void ownedIds(List<String> ids, Set<String> users, String name) {
        require(ids != null && !ids.isEmpty(), name + " must not be empty");
        Set<String> unique = new HashSet<>();
        for (String id : ids) {
            canonicalId(id, name);
            require(users.contains(id), name + " references an unowned user");
            require(unique.add(id), name + " contains duplicate user");
        }
    }

    private static void canonicalId(String value, String name) {
        require(value != null && value.matches("[1-9][0-9]*"), name + " is not canonical");
        try {
            Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " exceeds signed 64-bit range", exception);
        }
    }

    private static void nonEmpty(String value, String name) {
        require(value != null && !value.isEmpty(), name + " must not be empty");
    }

    private static boolean sameSet(List<String> left, List<String> right) {
        return left.size() == right.size() && new HashSet<>(left).equals(new HashSet<>(right));
    }

    private static <T> List<T> immutable(List<T> values, String name) {
        require(values != null, name + " is required");
        return List.copyOf(values);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    record Schemas(String account, String social, String message) {
    }

    record User(String userId, String email, String cohortId) {
    }

    record Cohort(String cohortId, List<String> userIds, String groupId) {
        Cohort {
            userIds = immutable(userIds, "cohort userIds");
        }
    }

    record Group(String groupId, String name, String ownerUserId, List<String> memberUserIds) {
        Group {
            memberUserIds = immutable(memberUserIds, "group memberUserIds");
        }
    }

    record Relation(String userId, String friendUserId) {
    }

    record PrivateChat(String chatId, String userId, String peerUserId) {
    }

    record GroupChat(String chatId, String groupId, String userId) {
    }

    record Message(String messageId, String type, String senderUserId, String groupId, String correlationToken) {
    }

    private record ChatFact(String type, String userId, String peerUserId, String groupId) {
    }
}
