package com.co.kc.imchat.broker.interfaces.handler.broker;

import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerHealthObservationDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerHealthReportDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerProbeDTO;
import com.co.kc.imchat.broker.model.cqrs.query.BrokerHealthReportQuery;
import com.co.kc.imchat.broker.model.cqrs.query.BrokerProbeQuery;
import com.co.kc.imchat.broker.sdk.enums.BrokerHealthStatus;
import com.co.kc.imchat.broker.sdk.model.params.BrokerHealthReportParams;
import com.co.kc.imchat.broker.sdk.model.params.BrokerProbeParams;
import com.co.kc.imchat.broker.sdk.model.result.BrokerHealthReportResult;
import com.co.kc.imchat.broker.sdk.model.result.BrokerProbeResult;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrokerHealthObservationHandlersTest {
    @Test
    void probeDelegatesExactQueryAndMapsEnvelopeWithoutReadIndex() throws Exception {
        BrokerClusterAppService app = mock(BrokerClusterAppService.class);
        BrokerProbeQuery query = new BrokerProbeQuery("cluster", "a", "b", 3L, 7L, "nonce");
        when(app.probeBroker(query)).thenReturn(new BrokerProbeDTO("cluster", "b", 3L, 7L, "nonce"));
        BrokerProbeResult result = (BrokerProbeResult) new BrokerProbeHandler(app).handleRequest(
                new BrokerProbeParams("cluster", "a", "b", 3L, 7L, "nonce"), null);
        assertThat(result).isEqualTo(new BrokerProbeResult("cluster", "b", 3L, 7L, "nonce"));
        verify(app).probeBroker(query);
        verifyNoMoreInteractions(app);
    }
    @Test
    void reportDelegatesExactTargetsAndMapsStatusesAndDurations() throws Exception {
        BrokerClusterAppService app = mock(BrokerClusterAppService.class);
        BrokerHealthReportQuery query = new BrokerHealthReportQuery("cluster", "a", "b", 3L, 7L, "nonce", List.of("c"));
        when(app.getHealthReport(query)).thenReturn(new BrokerHealthReportDTO("cluster", "b", 3L, 7L, "nonce",
                List.of(new BrokerHealthObservationDTO("c", com.co.kc.imchat.broker.domain.cluster.model.BrokerHealthStatus.UNHEALTHY, Duration.ofMillis(9), Duration.ofMillis(30000)))));
        BrokerHealthReportResult result = (BrokerHealthReportResult) new BrokerHealthReportHandler(app).handleRequest(
                new BrokerHealthReportParams("cluster", "a", "b", 3L, 7L, "nonce", List.of("c")), null);
        assertThat(result.clusterId()).isEqualTo("cluster");
        assertThat(result.observerBrokerId()).isEqualTo("b");
        assertThat(result.revision()).isEqualTo(3L);
        assertThat(result.epoch()).isEqualTo(7L);
        assertThat(result.nonce()).isEqualTo("nonce");
        assertThat(result.observations()).singleElement().satisfies(row -> {
            assertThat(row.brokerId()).isEqualTo("c");
            assertThat(row.status()).isEqualTo(BrokerHealthStatus.UNHEALTHY);
            assertThat(row.observationAgeMs()).isEqualTo(9L);
            assertThat(row.unreachableDurationMs()).isEqualTo(30000L);
        });
        verify(app).getHealthReport(query);
        verifyNoMoreInteractions(app);
    }
}
