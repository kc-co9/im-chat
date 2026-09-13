package com.co.kc.imchat.architecture;

import com.co.kc.imchat.plugin.datasource.ImDatasourceAutoConfiguration;
import com.co.kc.imchat.plugin.datasource.properties.ImShardingSphereProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "im.test.local.mysql", matches = "true")
class LocalShardingRoutingIntegrationTest {

    private static final Path REPO_ROOT = findRepositoryRoot();
    private static final long CHAT_ID = 9_999_991L;
    private static final long USER_ID = 9L;
    private static final long ROW_ID = 7_000_000_001L;
    private static final long COLLISION_ID = 7_000_000_002L;

    @Test
    void routesUserIdToModuloEightPhysicalTable() throws Exception {
        String mysqlUrl = System.getProperty(
                "im.test.mysql.url",
                "jdbc:mysql://localhost:3306/im_chat_message?characterEncoding=utf-8&serverTimezone=UTC");
        String username = System.getProperty("im.test.mysql.username", "root");
        String password = System.getProperty("im.test.mysql.password", "root");
        Path shardingConfig = REPO_ROOT.resolve(
                "im-service/im-message/im-message-server/src/main/resources/im-sharding.yml");

        deleteFixture(mysqlUrl, username, password);
        DataSource dataSource = createDataSource(shardingConfig, mysqlUrl, username, password);
        try {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO db_im_private_chat (id, chat_id, user_id, peer_user_id)
                         VALUES (?, ?, ?, ?)
                         """)) {
                statement.setLong(1, ROW_ID);
                statement.setLong(2, CHAT_ID);
                statement.setLong(3, USER_ID);
                statement.setLong(4, 10L);
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }

            try (Connection connection = DriverManager.getConnection(mysqlUrl, username, password);
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT COUNT(*) FROM db_im_private_chat_1 WHERE chat_id = ?")) {
                statement.setLong(1, CHAT_ID);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getInt(1)).isEqualTo(1);
                }
            }
        } finally {
            if (dataSource instanceof AutoCloseable closeable) {
                closeable.close();
            }
            deleteFixture(mysqlUrl, username, password);
        }
    }

    @Test
    void updateWithUserIdDoesNotModifySamePrimaryKeyInAnotherShard() throws Exception {
        String mysqlUrl = System.getProperty(
                "im.test.mysql.url",
                "jdbc:mysql://localhost:3306/im_chat_message?characterEncoding=utf-8&serverTimezone=UTC");
        String username = System.getProperty("im.test.mysql.username", "root");
        String password = System.getProperty("im.test.mysql.password", "root");
        Path shardingConfig = REPO_ROOT.resolve(
                "im-service/im-message/im-message-server/src/main/resources/im-sharding.yml");

        deleteCollisionFixture(mysqlUrl, username, password);
        insertCollisionRows(mysqlUrl, username, password);
        DataSource dataSource = createDataSource(shardingConfig, mysqlUrl, username, password);
        try {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         UPDATE db_im_private_chat
                         SET unread_message_count = ?, version = version + 1
                         WHERE id = ? AND user_id = ? AND version = ?
                         """)) {
                statement.setInt(1, 7);
                statement.setLong(2, COLLISION_ID);
                statement.setLong(3, 9L);
                statement.setLong(4, 0L);
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }

            assertShardState(mysqlUrl, username, password, "db_im_private_chat_1", 7, 1L);
            assertShardState(mysqlUrl, username, password, "db_im_private_chat_2", 0, 0L);
        } finally {
            if (dataSource instanceof AutoCloseable closeable) {
                closeable.close();
            }
            deleteCollisionFixture(mysqlUrl, username, password);
        }
    }

    private static void deleteFixture(String mysqlUrl, String username, String password) throws Exception {
        try (Connection connection = DriverManager.getConnection(mysqlUrl, username, password);
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM db_im_private_chat_1 WHERE chat_id = ?")) {
            statement.setLong(1, CHAT_ID);
            statement.executeUpdate();
        }
    }

    private static DataSource createDataSource(
            Path config,
            String mysqlUrl,
            String username,
            String password
    ) {
        ImShardingSphereProperties properties = new ImShardingSphereProperties();
        properties.setConfigLocation(config.toUri().toString());
        MockEnvironment environment = new MockEnvironment()
                .withProperty("im.datasource.sharding.jdbc-url", mysqlUrl)
                .withProperty("im.datasource.sharding.username", username)
                .withProperty("im.datasource.sharding.password", password)
                .withProperty("im.datasource.sharding.sql-show", "false");
        return new ImDatasourceAutoConfiguration().shardingSphereDataSource(
                properties, new DefaultResourceLoader(), environment);
    }

    private static void insertCollisionRows(String mysqlUrl, String username, String password) throws Exception {
        try (Connection connection = DriverManager.getConnection(mysqlUrl, username, password)) {
            for (long userId : new long[]{9L, 10L}) {
                String table = userId == 9L ? "db_im_private_chat_1" : "db_im_private_chat_2";
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO " + table + " (id, chat_id, user_id, peer_user_id) VALUES (?, ?, ?, ?)")) {
                    statement.setLong(1, COLLISION_ID);
                    statement.setLong(2, CHAT_ID + userId);
                    statement.setLong(3, userId);
                    statement.setLong(4, 99L);
                    statement.executeUpdate();
                }
            }
        }
    }

    private static void assertShardState(
            String mysqlUrl,
            String username,
            String password,
            String table,
            int unreadCount,
            long version
    ) throws Exception {
        try (Connection connection = DriverManager.getConnection(mysqlUrl, username, password);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT unread_message_count, version FROM " + table + " WHERE id = ?")) {
            statement.setLong(1, COLLISION_ID);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(unreadCount);
                assertThat(resultSet.getLong(2)).isEqualTo(version);
            }
        }
    }

    private static void deleteCollisionFixture(String mysqlUrl, String username, String password) throws Exception {
        try (Connection connection = DriverManager.getConnection(mysqlUrl, username, password)) {
            for (String table : new String[]{"db_im_private_chat_1", "db_im_private_chat_2"}) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM " + table + " WHERE id = ?")) {
                    statement.setLong(1, COLLISION_ID);
                    statement.executeUpdate();
                }
            }
        }
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("im-test/im-architecture-test"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root not found");
    }
}
