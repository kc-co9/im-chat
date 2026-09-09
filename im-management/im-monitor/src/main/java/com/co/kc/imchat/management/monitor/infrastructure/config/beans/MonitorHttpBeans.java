package com.co.kc.imchat.management.monitor.infrastructure.config.beans;

import com.co.kc.imchat.management.monitor.infrastructure.config.properties.MonitorBrokerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Monitor 出站 HTTP 客户端配置。
 */
@Configuration
public class MonitorHttpBeans {

    @Bean
    public RestClient brokerRestClient(RestClient.Builder builder, MonitorBrokerProperties properties) {
        return builder.requestFactory(requestFactory(properties)).build();
    }

    JdkClientHttpRequestFactory requestFactory(MonitorBrokerProperties properties) {
        Duration timeout = Duration.ofMillis(properties.getRequestTimeoutMillis());
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return requestFactory;
    }
}
