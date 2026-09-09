package com.co.kc.imchat.broker.interfaces.http.management;

import com.co.kc.imchat.broker.support.diagnostic.model.dto.BrokerInstanceDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.BrokerOverviewDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.BrokerStatisticsDTO;
import com.co.kc.imchat.broker.support.diagnostic.model.dto.DiagnosticSummaryDTO;
import com.co.kc.imchat.broker.support.diagnostic.service.BrokerDiagnosticService;
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

@WebMvcTest(BrokerManagementController.class)
@Import({ErrorAdvice.class, ResultAdvice.class})
class BrokerManagementControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrokerDiagnosticService diagnosticService;

    @Test
    void exposesReadOnlyManagementEndpoints() throws Exception {
        Instant startedAt = Instant.parse("2026-08-23T01:00:00Z");
        DiagnosticSummaryDTO empty = new DiagnosticSummaryDTO(0L, 0L, null, null, null);
        when(diagnosticService.overview()).thenReturn(new BrokerOverviewDTO(
                new BrokerInstanceDTO("broker-1", "127.0.0.1:12200", startedAt, 30L),
                new BrokerStatisticsDTO(1L, 2L, 3L), empty, empty));
        when(diagnosticService.brokers()).thenReturn(List.of());
        when(diagnosticService.gateways()).thenReturn(List.of());
        when(diagnosticService.connections(1L)).thenReturn(List.of());
        when(diagnosticService.gossipSummary()).thenReturn(empty);
        when(diagnosticService.gossipRecords(20)).thenReturn(List.of());
        when(diagnosticService.migrationRecords(20)).thenReturn(List.of());

        mockMvc.perform(get("/management/broker/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.broker.id").value("broker-1"))
                .andExpect(jsonPath("$.data.statistics.connectionCount").value(3));
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
        mockMvc.perform(get("/management/migrations"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/management/brokers"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    void validatesSingleUserAndRecordLimits() throws Exception {
        mockMvc.perform(get("/management/connections"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10006));
        mockMvc.perform(get("/management/connections").param("userId", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10006));
        mockMvc.perform(get("/management/gossip/records").param("limit", "101"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10006));
        mockMvc.perform(get("/management/migrations").param("limit", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10006));
        mockMvc.perform(get("/management/connections/all"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10000));
    }
}
