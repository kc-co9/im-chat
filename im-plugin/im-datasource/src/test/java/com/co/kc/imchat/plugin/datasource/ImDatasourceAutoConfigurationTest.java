package com.co.kc.imchat.plugin.datasource;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.co.kc.imchat.plugin.datasource.properties.ImShardingSphereProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ImDatasourceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ImDatasourceAutoConfiguration.class);

    @Test
    void shardingSphereDataSourceIsDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(DataSource.class);
            assertThat(context.getBean(ImShardingSphereProperties.class).isEnabled()).isFalse();
        });
    }

    @Test
    void shardingSphereAutoConfigurationRunsBeforeDefaultDataSourceAutoConfiguration() {
        AutoConfigureBefore annotation = ImDatasourceAutoConfiguration.class.getAnnotation(AutoConfigureBefore.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(DataSourceAutoConfiguration.class);
    }

    @Test
    void configuresMybatisJsonHandlerWithTheApplicationObjectMapper() {
        ObjectMapper previousObjectMapper = JacksonTypeHandler.getObjectMapper();
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        try {
            contextRunner.withBean(ObjectMapper.class, () -> objectMapper).run(context -> {
                assertThat(JacksonTypeHandler.getObjectMapper()).isSameAs(objectMapper);
                assertThatCode(() -> JacksonTypeHandler.getObjectMapper().writeValueAsString(
                        Map.of("exp", Instant.parse("2026-09-07T15:18:47Z"))))
                        .doesNotThrowAnyException();
            });
        } finally {
            JacksonTypeHandler.setObjectMapper(previousObjectMapper);
        }
    }

    @Test
    void bindsShardingSphereConfigLocation() {
        contextRunner
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .withPropertyValues(
                        "im.datasource.sharding.enabled=true",
                        "im.datasource.sharding.config-location=classpath:sharding.yml")
                .run(context -> {
                    ImShardingSphereProperties properties = context.getBean(ImShardingSphereProperties.class);

                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getConfigLocation()).isEqualTo("classpath:sharding.yml");
                });
    }

    @Test
    void closesShardingSphereConfigInputStreamWhenReadFails() {
        ImDatasourceAutoConfiguration configuration = new ImDatasourceAutoConfiguration();
        ImShardingSphereProperties properties = new ImShardingSphereProperties();
        properties.setConfigLocation("memory:broken-sharding.yml");
        TrackingInputStream inputStream = new TrackingInputStream();
        Resource resource = new AbstractResource() {
            @Override
            public String getDescription() {
                return "broken sharding config";
            }

            @Override
            public boolean exists() {
                return true;
            }

            @Override
            public InputStream getInputStream() {
                return inputStream;
            }
        };

        ResourceLoader resourceLoader = new ResourceLoader() {
            @Override
            public Resource getResource(String location) {
                return resource;
            }

            @Override
            public ClassLoader getClassLoader() {
                return getClass().getClassLoader();
            }
        };

        assertThrows(BeanCreationException.class,
                () -> configuration.shardingSphereDataSource(
                        properties, resourceLoader, new MockEnvironment()));
        assertThat(inputStream.closed).isTrue();
    }

    @Test
    void resolvesSpringEnvironmentPlaceholdersBeforeCreatingDataSource() throws Exception {
        ImDatasourceAutoConfiguration configuration = new ImDatasourceAutoConfiguration();
        ImShardingSphereProperties properties = new ImShardingSphereProperties();
        properties.setConfigLocation("memory:sharding.yml");
        String yaml = """
                dataSources:
                  ds_0:
                    dataSourceClassName: com.zaxxer.hikari.HikariDataSource
                    driverClassName: org.h2.Driver
                    jdbcUrl: ${im.datasource.sharding.jdbc-url}
                    username: ${im.datasource.sharding.username}
                    password: ${im.datasource.sharding.password}
                rules:
                  - !SINGLE
                    defaultDataSource: ds_0
                  - !SHARDING
                    tables:
                      sample:
                        actualDataNodes: ds_0.sample_${0..1}
                        tableStrategy:
                          standard:
                            shardingColumn: id
                            shardingAlgorithmName: sample-inline
                    shardingAlgorithms:
                      sample-inline:
                        type: INLINE
                        props:
                          algorithm-expression: sample_${id % 2}
                """;
        ResourceLoader loader = new ResourceLoader() {
            @Override
            public Resource getResource(String location) {
                return new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public ClassLoader getClassLoader() {
                return getClass().getClassLoader();
            }
        };
        MockEnvironment environment = new MockEnvironment()
                .withProperty("im.datasource.sharding.jdbc-url", "jdbc:h2:mem:placeholder-config")
                .withProperty("im.datasource.sharding.username", "sa")
                .withProperty("im.datasource.sharding.password", "");

        try (AutoCloseableDataSource dataSource = new AutoCloseableDataSource(
                configuration.shardingSphereDataSource(properties, loader, environment))) {
            try (java.sql.Connection connection = dataSource.delegate().getConnection()) {
                assertThat(connection.getMetaData().getURL())
                        .isEqualTo("jdbc:h2:mem:placeholder-config");
            }
        }
    }

    @Test
    void resolvesSqlShowOverrideWithoutConsumingShardingSphereExpressions() {
        String yaml = """
                actualDataNodes: ds_0.sample_${0..7}
                algorithm-expression: sample_${user_id % 8}
                props:
                  sql-show: ${im.datasource.sharding.sql-show:true}
                """;
        MockEnvironment environment = new MockEnvironment()
                .withProperty("im.datasource.sharding.sql-show", "false");

        assertThat(ImDatasourceAutoConfiguration.resolveSpringShardingPlaceholders(yaml, environment))
                .contains("actualDataNodes: ds_0.sample_${0..7}")
                .contains("algorithm-expression: sample_${user_id % 8}")
                .contains("sql-show: false");
    }

    private record AutoCloseableDataSource(DataSource delegate) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            if (delegate instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    private static class TrackingInputStream extends InputStream {
        private boolean closed;

        @Override
        public int read() throws IOException {
            throw new IOException("broken stream");
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
