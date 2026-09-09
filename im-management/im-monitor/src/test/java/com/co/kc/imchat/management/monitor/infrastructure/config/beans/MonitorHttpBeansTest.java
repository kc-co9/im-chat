package com.co.kc.imchat.management.monitor.infrastructure.config.beans;

import com.co.kc.imchat.management.monitor.infrastructure.config.properties.MonitorBrokerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorHttpBeansTest {

    @Test
    void appliesConfiguredConnectAndReadTimeout() {
        MonitorBrokerProperties properties = new MonitorBrokerProperties();
        properties.setRequestTimeoutMillis(25);

        JdkClientHttpRequestFactory factory = new MonitorHttpBeans().requestFactory(properties);
        HttpClient httpClient = (HttpClient) ReflectionTestUtils.getField(factory, "httpClient");

        assertThat(httpClient).isNotNull();
        assertThat(httpClient.connectTimeout()).contains(Duration.ofMillis(25));
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout"))
                .isEqualTo(Duration.ofMillis(25));
    }
}
