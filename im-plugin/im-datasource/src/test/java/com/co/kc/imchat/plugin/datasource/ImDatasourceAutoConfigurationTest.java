package com.co.kc.imchat.plugin.datasource;

import com.co.kc.imchat.plugin.datasource.properties.ImShardingSphereProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
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
                () -> configuration.shardingSphereDataSource(properties, resourceLoader));
        assertThat(inputStream.closed).isTrue();
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
