package com.co.kc.imchat.plugin.metrics;

import com.co.kc.imchat.plugin.metrics.annotation.Observed;
import com.co.kc.imchat.plugin.metrics.aspect.ObservedAspect;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = ImMetricsManagementEndpointTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "management.endpoints.web.exposure.include=health,prometheus",
                "management.endpoint.health.show-details=never"
        })
@AutoConfigureObservability
class ImMetricsManagementEndpointTest {

    @LocalManagementPort
    private int managementPort;

    @LocalServerPort
    private int businessPort;

    @Autowired
    private ObservedOperation observedOperation;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void prometheusEndpointBypassesTheBusinessSecurityChain() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + managementPort + "/actuator/prometheus"))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode())
                .as(response.headers() + System.lineSeparator() + response.body())
                .isEqualTo(200);
        assertThat(response.body()).contains("# HELP");
    }

    @Test
    void businessEndpointRemainsProtected() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + businessPort + "/business"))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void observedMethodExportsCountersAndDurationThroughPrometheus() throws Exception {
        assertThat(applicationContext.getBeanNamesForType(ObservedAspect.class)).contains("observedAspect");
        observedOperation.run();

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + managementPort + "/actuator/prometheus"))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("test_observed_export_total{outcome=\"success\"} 1.0")
                .contains("test_observed_export_duration_seconds_count 1");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({DenyAllSecurityConfiguration.class, TestController.class, ObservedOperation.class})
    static class TestApplication {
    }

    @Configuration(proxyBeanMethods = false)
    static class DenyAllSecurityConfiguration {

        @Bean
        @Order(0)
        SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http,
                                                        WebEndpointProperties endpointProperties)
                throws Exception {
            RequestMatcher endpointMatcher = PathPatternRequestMatcher.withDefaults()
                    .matcher(endpointProperties.getBasePath() + "/**");
            return http.securityMatcher(endpointMatcher)
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .csrf(AbstractHttpConfigurer::disable)
                    .build();
        }

        @Bean
        @Order(10)
        SecurityFilterChain denyAllSecurityFilterChain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(authorize -> authorize.anyRequest().denyAll()).build();
        }
    }

    @RestController
    static class TestController {

        @GetMapping("/business")
        String business() {
            return "protected";
        }
    }

    static class ObservedOperation {
        @Observed(name = "test.observed.export")
        public void run() {
        }
    }
}
