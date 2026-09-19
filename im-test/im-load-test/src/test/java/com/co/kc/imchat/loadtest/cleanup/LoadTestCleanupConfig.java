package com.co.kc.imchat.loadtest.cleanup;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

final class LoadTestCleanupConfig {

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1");

    private final DatabaseTarget account;
    private final DatabaseTarget social;
    private final DatabaseTarget message;
    private final RedisTarget redis;

    private LoadTestCleanupConfig(DatabaseTarget account, DatabaseTarget social, DatabaseTarget message,
            RedisTarget redis) {
        this.account = account;
        this.social = social;
        this.message = message;
        this.redis = redis;
    }

    static LoadTestCleanupConfig fromSources(Map<String, String> environment, Map<String, String> systemProperties) {
        return create(
                required(environment, systemProperties, "LOAD_TEST_CLEANUP_ACCOUNT_URL", "loadtest.cleanup.account.url"),
                required(environment, systemProperties, "LOAD_TEST_CLEANUP_ACCOUNT_USER", "loadtest.cleanup.account.user"),
                required(environment, systemProperties, "LOAD_TEST_CLEANUP_ACCOUNT_PASSWORD",
                        "loadtest.cleanup.account.password"),
                required(environment, systemProperties, "LOAD_TEST_CLEANUP_SOCIAL_URL", "loadtest.cleanup.social.url"),
                required(environment, systemProperties, "LOAD_TEST_CLEANUP_SOCIAL_USER", "loadtest.cleanup.social.user"),
                required(environment, systemProperties, "LOAD_TEST_CLEANUP_SOCIAL_PASSWORD",
                        "loadtest.cleanup.social.password"),
                required(environment, systemProperties, "LOAD_TEST_CLEANUP_MESSAGE_URL", "loadtest.cleanup.message.url"),
                required(environment, systemProperties, "LOAD_TEST_CLEANUP_MESSAGE_USER", "loadtest.cleanup.message.user"),
                required(environment, systemProperties, "LOAD_TEST_CLEANUP_MESSAGE_PASSWORD",
                        "loadtest.cleanup.message.password"),
                required(environment, systemProperties, "LOAD_TEST_CLEANUP_REDIS_URL", "loadtest.cleanup.redis.url"),
                required(environment, systemProperties, "LOAD_TEST_CLEANUP_REDIS_PASSWORD",
                        "loadtest.cleanup.redis.password"));
    }

    static RedisTarget redisTarget(String redisUrl, String password) {
        return redis(redisUrl, password);
    }

    static LoadTestCleanupConfig create(
            String accountUrl, String accountUser, String accountPassword,
            String socialUrl, String socialUser, String socialPassword,
            String messageUrl, String messageUser, String messagePassword,
            String redisUrl, String redisPassword) {
        return new LoadTestCleanupConfig(
                database(accountUrl, accountUser, accountPassword, "im_chat_account"),
                database(socialUrl, socialUser, socialPassword, "im_chat_social"),
                database(messageUrl, messageUser, messagePassword, "im_chat_message"),
                redis(redisUrl, redisPassword));
    }

    DatabaseTarget account() {
        return account;
    }

    DatabaseTarget social() {
        return social;
    }

    DatabaseTarget message() {
        return message;
    }

    RedisTarget redis() {
        return redis;
    }

    @Override
    public String toString() {
        return "LoadTestCleanupConfig[account=" + account.safeDescription()
                + ", social=" + social.safeDescription()
                + ", message=" + message.safeDescription()
                + ", redis=" + redis.safeDescription() + "]";
    }

    private static DatabaseTarget database(String jdbcUrl, String username, String password, String schema) {
        URI uri = parse(jdbcUrl != null && jdbcUrl.startsWith("jdbc:") ? jdbcUrl.substring(5) : null,
                "invalid MySQL cleanup target");
        require("mysql".equals(uri.getScheme()), "invalid MySQL cleanup target");
        validateEndpoint(uri, "invalid MySQL cleanup target");
        require(uri.getUserInfo() == null, "invalid MySQL cleanup target");
        require(("/" + schema).equals(uri.getPath()), "MySQL cleanup schema mismatch");
        require(username != null && !username.isBlank() && password != null, "missing MySQL cleanup credentials");
        return new DatabaseTarget(jdbcUrl, schema, username, chars(password));
    }

    private static RedisTarget redis(String redisUrl, String password) {
        URI uri = parse(redisUrl, "invalid Redis cleanup target");
        require(Set.of("redis", "rediss").contains(uri.getScheme()), "invalid Redis cleanup target");
        validateEndpoint(uri, "invalid Redis cleanup target");
        require(uri.getUserInfo() == null && (uri.getPath().isEmpty() || "/".equals(uri.getPath())),
                "invalid Redis cleanup target");
        return new RedisTarget(redisUrl, chars(password));
    }

    private static URI parse(String value, String message) {
        try {
            return URI.create(value == null ? "" : value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void validateEndpoint(URI uri, String message) {
        require(LOOPBACK_HOSTS.contains(uri.getHost()), message);
        require(uri.getPort() > 0, message);
        require(uri.getRawQuery() == null && uri.getRawFragment() == null, message);
    }

    private static String required(Map<String, String> environment, Map<String, String> systemProperties,
            String environmentName, String propertyName) {
        if (systemProperties.containsKey(propertyName)) {
            return systemProperties.get(propertyName);
        }
        if (environment.containsKey(environmentName)) {
            return environment.get(environmentName);
        }
        throw new IllegalArgumentException("missing explicit cleanup configuration");
    }

    private static char[] chars(String value) {
        return value == null ? new char[0] : value.toCharArray();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    static final class DatabaseTarget {
        private final String jdbcUrl;
        private final String schema;
        private final String username;
        private final char[] password;

        private DatabaseTarget(String jdbcUrl, String schema, String username, char[] password) {
            this.jdbcUrl = jdbcUrl;
            this.schema = schema;
            this.username = username;
            this.password = password;
        }

        String jdbcUrl() {
            return jdbcUrl;
        }

        String schema() {
            return schema;
        }

        String username() {
            return username;
        }

        char[] password() {
            return Arrays.copyOf(password, password.length);
        }

        private String safeDescription() {
            URI uri = URI.create(jdbcUrl.substring(5));
            return uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort() + "/" + schema;
        }
    }

    static final class RedisTarget {
        private final String url;
        private final char[] password;

        private RedisTarget(String url, char[] password) {
            this.url = url;
            this.password = password;
        }

        String url() {
            return url;
        }

        char[] password() {
            return Arrays.copyOf(password, password.length);
        }

        private String safeDescription() {
            URI uri = URI.create(url);
            return uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort();
        }
    }
}
