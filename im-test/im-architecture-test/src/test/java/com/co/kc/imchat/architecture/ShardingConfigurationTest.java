package com.co.kc.imchat.architecture;

import org.apache.shardingsphere.driver.yaml.YamlJDBCConfiguration;
import org.apache.shardingsphere.infra.algorithm.core.yaml.YamlAlgorithmConfiguration;
import org.apache.shardingsphere.infra.util.yaml.YamlEngine;
import org.apache.shardingsphere.sharding.yaml.config.YamlShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.yaml.config.rule.YamlTableRuleConfiguration;
import org.apache.shardingsphere.single.yaml.config.YamlSingleRuleConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShardingConfigurationTest {

    private static final Path REPO_ROOT = findRepositoryRoot();
    private static final List<String> SINGLE_TABLE_CONFIGS = List.of(
            "im-service/im-account/im-account-server/src/main/resources/im-sharding.yml",
            "im-service/im-social/im-social-server/src/main/resources/im-sharding.yml",
            "im-management/im-iam/im-iam-server/src/main/resources/im-sharding.yml",
            "im-management/im-audit/im-audit-server/src/main/resources/im-sharding.yml");
    private static final List<String> DATABASE_APPLICATION_CONFIGS = List.of(
            "im-service/im-account/im-account-server/src/main/resources/application.yml",
            "im-service/im-social/im-social-server/src/main/resources/application.yml",
            "im-service/im-message/im-message-server/src/main/resources/application.yml",
            "im-management/im-iam/im-iam-server/src/main/resources/application.yml",
            "im-management/im-audit/im-audit-server/src/main/resources/application.yml");
    private static final String MESSAGE_CONFIG =
            "im-service/im-message/im-message-server/src/main/resources/im-sharding.yml";
    private static final Set<String> MESSAGE_TABLES = Set.of(
            "db_im_private_chat",
            "db_im_group_chat",
            "db_im_private_inbox_message",
            "db_im_group_inbox_message");

    @Test
    void nonMessageDatabaseModulesUseSingleTableRule() throws IOException {
        for (String configPath : SINGLE_TABLE_CONFIGS) {
            YamlJDBCConfiguration configuration = load(configPath);

            assertDataSource(configuration, configPath);
            assertThat(configuration.getProps())
                    .as(configPath)
                    .containsEntry("sql-show", "${im.datasource.sharding.sql-show:true}");
            assertThat(configuration.getRules())
                    .as(configPath)
                    .singleElement()
                    .isInstanceOfSatisfying(YamlSingleRuleConfiguration.class,
                            rule -> {
                                assertThat(rule.getDefaultDataSource()).isEqualTo("ds_0");
                                assertThat(rule.getTables()).containsExactly("ds_0.*");
                            });
        }
    }

    @Test
    void databaseApplicationsUseShardingConfigurationAsTheOnlyDataSourceDefinition() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String configPath : DATABASE_APPLICATION_CONFIGS) {
            List<PropertySource<?>> sources = loader.load(configPath,
                    new FileSystemResource(REPO_ROOT.resolve(configPath)));

            assertThat(property(sources, "im.datasource.sharding.enabled")).as(configPath).isEqualTo(true);
            assertThat(property(sources, "im.datasource.sharding.config-location"))
                    .as(configPath)
                    .isEqualTo("classpath:im-sharding.yml");
            assertThat(property(sources, "spring.datasource.url")).as(configPath).isNull();
            assertThat(property(sources, "spring.datasource.username")).as(configPath).isNull();
            assertThat(property(sources, "spring.datasource.password")).as(configPath).isNull();
        }
    }

    @Test
    void messageTablesUseEightUserIdShards() throws IOException {
        YamlJDBCConfiguration configuration = load(MESSAGE_CONFIG);

        assertDataSource(configuration, MESSAGE_CONFIG);
        assertThat(configuration.getProps())
                .as(MESSAGE_CONFIG)
                .containsEntry("sql-show", "${im.datasource.sharding.sql-show:true}");
        assertThat(configuration.getDataSources().get("ds_0"))
                .containsEntry("minimumIdle", 50)
                .containsEntry("maximumPoolSize", 200)
                .containsEntry("connectionTimeout", 60000)
                .containsEntry("idleTimeout", 300000);
        assertThat(configuration.getRules())
                .singleElement()
                .isInstanceOfSatisfying(YamlShardingRuleConfiguration.class, rule -> {
                    assertThat(rule.getTables()).containsOnlyKeys(MESSAGE_TABLES);
                    assertThat(rule.getShardingAlgorithms()).containsOnlyKeys(
                            MESSAGE_TABLES.stream().map(table -> table + "_inline").toList());
                    rule.getTables().forEach((tableName, tableRule) ->
                            assertMessageTableRule(tableName, tableRule, rule.getShardingAlgorithms()));
                });
    }

    @Test
    void messageDdlCreatesEveryConfiguredPhysicalTable() throws IOException {
        String ddl = Files.readString(REPO_ROOT.resolve(
                "im-service/im-message/im-message-server/sql/ddl.sql"));

        for (String tableName : MESSAGE_TABLES) {
            assertThat(ddl).doesNotContain("CREATE TABLE IF NOT EXISTS `" + tableName + "`\n");
            assertThat(ddl).contains("CREATE TABLE IF NOT EXISTS `" + tableName + "_0`");
            for (int shard = 1; shard < 8; shard++) {
                assertThat(ddl).contains("CREATE TABLE IF NOT EXISTS `" + tableName + "_" + shard
                        + "` LIKE `" + tableName + "_0`");
            }
        }
    }

    private static YamlJDBCConfiguration load(String relativePath) throws IOException {
        return YamlEngine.unmarshal(REPO_ROOT.resolve(relativePath).toFile(), YamlJDBCConfiguration.class);
    }

    private static Object property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    private static void assertDataSource(YamlJDBCConfiguration configuration, String configPath) {
        assertThat(configuration.getDataSources()).as(configPath).containsOnlyKeys("ds_0");
        Map<String, Object> dataSource = configuration.getDataSources().get("ds_0");
        assertThat(dataSource)
                .containsEntry("dataSourceClassName", "com.zaxxer.hikari.HikariDataSource")
                .containsEntry("driverClassName", "com.mysql.cj.jdbc.Driver")
                .containsKeys("url", "username", "password")
                .doesNotContainKey("props");
    }

    private static void assertMessageTableRule(String tableName,
                                               YamlTableRuleConfiguration tableRule,
                                               Map<String, YamlAlgorithmConfiguration> algorithms) {
        String algorithmName = tableName + "_inline";
        assertThat(tableRule.getActualDataNodes()).isEqualTo("ds_0." + tableName + "_${0..7}");
        assertThat(tableRule.getTableStrategy().getStandard().getShardingColumn()).isEqualTo("user_id");
        assertThat(tableRule.getTableStrategy().getStandard().getShardingAlgorithmName()).isEqualTo(algorithmName);
        assertThat(algorithms.get(algorithmName).getType()).isEqualTo("INLINE");
        assertThat(algorithms.get(algorithmName).getProps())
                .containsEntry("algorithm-expression", tableName + "_${user_id % 8}");
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
