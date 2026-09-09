package com.co.kc.imchat.management.monitor.interfaces.http;

import com.co.kc.imchat.management.monitor.application.MonitorQueryAppService;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.BrokerInstanceDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.BrokerNodeOverviewDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.BrokerOverviewDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.BrokerStatisticsDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.ClusterOverviewDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.DiagnosticSummaryDTO;
import com.co.kc.imchat.management.monitor.domain.model.NodeStatus;
import com.co.kc.imchat.management.monitor.model.io.MonitorOverviewResponse;
import com.co.kc.imchat.management.monitor.transformer.interfaces.MonitorHttpTransformer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorBoundaryTest {

    @Test
    void controllerReturnsOnlyHttpResponseModels() {
        assertThat(Arrays.stream(MonitorController.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getReturnType().getSimpleName()))
                .allMatch(name -> name.endsWith("Response"));
    }

    @Test
    void applicationUseCasesAcceptOneQueryObject() {
        assertThat(Arrays.stream(MonitorQueryAppService.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers())))
                .allMatch(method -> method.getParameterCount() == 1
                        && method.getParameterTypes()[0].getSimpleName().endsWith("Query"));
    }

    @Test
    void mapsNestedAbsoluteTimesToEpochMilliseconds() {
        Instant queriedAt = Instant.parse("2026-08-23T01:00:00Z");
        BrokerOverviewDTO data = new BrokerOverviewDTO(
                new BrokerInstanceDTO("broker-1", "127.0.0.1:12200", queriedAt, 10L),
                new BrokerStatisticsDTO(1L, 2L, 3L),
                new DiagnosticSummaryDTO(0L, 0L, null, null, queriedAt),
                new DiagnosticSummaryDTO(0L, 0L, null, null, queriedAt));
        ClusterOverviewDTO overview = new ClusterOverviewDTO(1L, 2L, 3L,
                List.of(new BrokerNodeOverviewDTO(
                        "broker-1", NodeStatus.HEALTHY, data, null, queriedAt)),
                queriedAt);

        MonitorOverviewResponse response =
                MonitorHttpTransformer.INSTANCE.monitorOverviewResponseFrom(overview);

        assertThat(response.queriedAt()).isEqualTo(1787446800000L);
        assertThat(response.nodes().getFirst().data().broker().startedAt())
                .isEqualTo(1787446800000L);
    }
}
