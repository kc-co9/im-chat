package com.co.kc.imchat.management.ops.infrastructure.config.beans;

import com.co.kc.imchat.management.ops.infrastructure.config.properties.OpsBrokerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OpsHttpBeansTest {

    @Test
    void appliesConfiguredConnectAndReadTimeout() {
        OpsBrokerProperties properties = new OpsBrokerProperties();
        properties.setRequestTimeoutMillis(25);

        JdkClientHttpRequestFactory factory = new OpsHttpBeans().requestFactory(properties);
        HttpClient httpClient = (HttpClient) ReflectionTestUtils.getField(factory, "httpClient");

        assertThat(httpClient).isNotNull();
        assertThat(httpClient.connectTimeout()).contains(Duration.ofMillis(25));
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout"))
                .isEqualTo(Duration.ofMillis(25));
    }
}
