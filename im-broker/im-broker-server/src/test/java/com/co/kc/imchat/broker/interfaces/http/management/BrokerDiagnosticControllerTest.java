package com.co.kc.imchat.broker.interfaces.http.management;

import com.co.kc.imchat.broker.model.cqrs.query.GossipRecordsQuery;

import com.co.kc.imchat.broker.model.cqrs.query.ConnectionRoutesQuery;

import com.co.kc.imchat.broker.model.cqrs.dto.BrokerInstanceDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerOverviewDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.BrokerStatisticsDTO;
import com.co.kc.imchat.broker.model.cqrs.dto.DiagnosticSummaryDTO;
import com.co.kc.imchat.broker.application.diagnostic.BrokerDiagnosticAppService;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerManagementProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import com.co.kc.imchat.plugin.web.advice.ErrorAdvice;
import com.co.kc.imchat.plugin.web.advice.ResultAdvice;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrokerDiagnosticController.class)
@Import({ErrorAdvice.class, ResultAdvice.class, com.co.kc.imchat.plugin.web.ImWebAutoConfiguration.class})
@EnableConfigurationProperties(BrokerManagementProperties.class)
class BrokerDiagnosticControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrokerDiagnosticAppService diagnosticService;

    @Test
    void exposesReadOnlyManagementEndpoints() throws Exception {
        Instant startedAt = Instant.parse("2026-08-23T01:00:00Z");
        DiagnosticSummaryDTO empty = new DiagnosticSummaryDTO(0L, 0L, null, null, null);
        when(diagnosticService.overview()).thenReturn(new BrokerOverviewDTO(
                new BrokerInstanceDTO("broker-1", "127.0.0.1:12200", startedAt, java.time.Duration.ofMillis(30001)),
                new BrokerStatisticsDTO(1L, 2L, 3L), empty));
        when(diagnosticService.brokers()).thenReturn(List.of());
        when(diagnosticService.gateways()).thenReturn(List.of());
        when(diagnosticService.connections(new ConnectionRoutesQuery(1L))).thenReturn(List.of());
        when(diagnosticService.gossipSummary()).thenReturn(empty);
        when(diagnosticService.gossipRecords(new GossipRecordsQuery(20))).thenReturn(List.of());

        mockMvc.perform(get("/management/broker/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.broker.id").value("broker-1"))
                .andExpect(jsonPath("$.data.statistics.connectionCount").value("3"))
                .andExpect(jsonPath("$.data.broker.startedAtMs").value("1787446800000"))
                .andExpect(jsonPath("$.data.broker.uptimeMs").value("30001"))
                .andExpect(jsonPath("$.data.broker.startedAt").doesNotExist())
                .andExpect(jsonPath("$.data.broker.uptimeSeconds").doesNotExist())
                .andExpect(jsonPath("$.data.gossip.lastSuccessAtMs").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.gossip.lastExecutedAtMs").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(get("/management/brokers"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/management/gateways"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/management/connections").param("userId", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/management/gossip"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/management/gossip/records"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/management/brokers"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    void serializesRegistryListsAndGossipAsMillisecondStrings() throws Exception {
        Instant at = Instant.ofEpochMilli(1787446800123L);
        when(diagnosticService.brokers()).thenReturn(List.of(new com.co.kc.imchat.broker.model.cqrs.dto.BrokerNodeDTO("b", "host", 12200, at, at)));
        when(diagnosticService.gateways()).thenReturn(List.of(new com.co.kc.imchat.broker.model.cqrs.dto.GatewayNodeDTO("g", "host", 12300, at, at)));
        when(diagnosticService.connections(new ConnectionRoutesQuery(1L))).thenReturn(List.of(new com.co.kc.imchat.broker.model.cqrs.dto.ConnectionRouteDTO(1L, "g", at, at)));
        when(diagnosticService.gossipRecords(new GossipRecordsQuery(20))).thenReturn(List.of(new com.co.kc.imchat.broker.model.cqrs.dto.GossipRecordDTO(at, "b", com.co.kc.imchat.broker.model.enums.DiagnosticStatus.SUCCESS, 1, java.time.Duration.ofMillis(1001), null)));
        for (String url : List.of("/management/brokers", "/management/gateways", "/management/connections?userId=1")) {
            mockMvc.perform(get(url)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].registeredAtMs").value("1787446800123"))
                    .andExpect(jsonPath("$.data[0].lastSeenAtMs").value("1787446800123"))
                    .andExpect(jsonPath("$.data[0].registeredAt").doesNotExist())
                    .andExpect(jsonPath("$.data[0].lastSeenAt").doesNotExist());
        }
        mockMvc.perform(get("/management/gossip/records"))
                .andExpect(jsonPath("$.data[0].executedAtMs").value("1787446800123"))
                .andExpect(jsonPath("$.data[0].durationMs").value("1001"))
                .andExpect(jsonPath("$.data[0].durationMillis").doesNotExist())
                .andExpect(jsonPath("$.data[0].executedAt").doesNotExist());
    }

    @Test
    void validatesSingleUserAndRecordLimits() throws Exception {
        mockMvc.perform(get("/management/connections"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10006));
        mockMvc.perform(get("/management/connections").param("userId", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10006));
        mockMvc.perform(get("/management/gossip/records").param("limit", "101"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10006));
        mockMvc.perform(get("/management/connections/all"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10000));
    }
}
