package com.co.kc.imchat.management.ops.interfaces.http;

import com.co.kc.imchat.management.ops.application.OpsQueryAppService;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerInstanceDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerNodeOverviewDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerOverviewDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerStatisticsDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.ClusterOverviewDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.DiagnosticSummaryDTO;
import com.co.kc.imchat.management.ops.domain.model.NodeStatus;
import com.co.kc.imchat.management.ops.model.io.OpsOverviewResponse;
import com.co.kc.imchat.management.ops.transformer.interfaces.OpsHttpTransformer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpsBoundaryTest {

    @Test
    void controllerReturnsOnlyHttpResponseModels() {
        assertThat(Arrays.stream(OpsController.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getReturnType().getSimpleName()))
                .allMatch(name -> name.endsWith("Response"));
    }

    @Test
    void applicationUseCasesAcceptOneQueryObject() {
        assertThat(Arrays.stream(OpsQueryAppService.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers())))
                .allMatch(method -> method.getParameterCount() == 1
                        && method.getParameterTypes()[0].getSimpleName().endsWith("Query"));
    }

    @Test
    void mapsNestedAbsoluteTimesToEpochMilliseconds() throws Exception {
        Instant queriedAt = Instant.parse("2026-08-23T01:00:00Z");
        BrokerOverviewDTO data = new BrokerOverviewDTO(
                new BrokerInstanceDTO("broker-1", "127.0.0.1:12200", queriedAt, java.time.Duration.ofMillis(10001)),
                new BrokerStatisticsDTO(1L, 2L, 3L),
                new DiagnosticSummaryDTO(0L, 0L, null, null, queriedAt));
        ClusterOverviewDTO overview = new ClusterOverviewDTO(1L, 2L, 3L,
                List.of(new BrokerNodeOverviewDTO(
                        "broker-1", NodeStatus.HEALTHY, data, null, queriedAt)),
                queriedAt);

        OpsOverviewResponse response =
                OpsHttpTransformer.INSTANCE.opsOverviewResponseFrom(overview);

        assertThat(response.queriedAt()).isEqualTo(1787446800000L);
        assertThat(response.nodes().getFirst().data().broker().startedAtMs())
                .isEqualTo(1787446800000L);
        assertThat(response.nodes().getFirst().data().broker().uptimeMs()).isEqualTo(10001L);
        assertThat(response.nodes().getFirst().data().gossip().lastFailureAtMs()).isNull();
        assertThat(response.nodes().getFirst().data().gossip().lastExecutedAtMs()).isEqualTo(1787446800000L);
        org.springframework.http.converter.json.Jackson2ObjectMapperBuilder builder =
                org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json();
        new com.co.kc.imchat.plugin.web.ImWebAutoConfiguration(
                new org.springframework.beans.factory.support.DefaultListableBeanFactory()
                        .getBeanProvider(com.co.kc.imchat.plugin.web.mvc.ImWebMvcInterceptor.class))
                .imJacksonObjectMapperCustomization().customize(builder);
        com.fasterxml.jackson.databind.JsonNode json = builder.build().readTree(builder.build().writeValueAsString(response));
        com.fasterxml.jackson.databind.JsonNode dataJson = json.path("nodes").get(0).path("data");
        assertThat(dataJson.path("broker").path("startedAtMs").textValue()).isEqualTo("1787446800000");
        assertThat(dataJson.path("broker").path("uptimeMs").textValue()).isEqualTo("10001");
        assertThat(dataJson.path("broker").has("startedAt")).isFalse();
        assertThat(dataJson.path("broker").has("uptimeSeconds")).isFalse();
        assertThat(dataJson.path("gossip").path("lastFailureAtMs").isNull()).isTrue();
    }
}
