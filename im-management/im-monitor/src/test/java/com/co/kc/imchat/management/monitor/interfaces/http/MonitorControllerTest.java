package com.co.kc.imchat.management.monitor.interfaces.http;

import com.co.kc.imchat.management.monitor.application.MonitorQueryAppService;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.ClusterOverviewDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.dto.ClusterQueryResultDTO;
import com.co.kc.imchat.management.monitor.model.cqrs.query.BrokerListQuery;
import com.co.kc.imchat.management.monitor.model.cqrs.query.ConnectionQuery;
import com.co.kc.imchat.management.monitor.model.cqrs.query.DiagnosticQuery;
import com.co.kc.imchat.management.monitor.model.cqrs.query.GatewayListQuery;
import com.co.kc.imchat.management.monitor.model.cqrs.query.OverviewQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Import;
import com.co.kc.imchat.management.monitor.infrastructure.config.beans.MonitorSecurityBeans;
import com.co.kc.imchat.plugin.web.advice.ErrorAdvice;
import com.co.kc.imchat.plugin.web.advice.ResultAdvice;
import com.co.kc.imchat.management.iam.sdk.security.IamSecurityExceptionHandler;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MonitorController.class)
@Import({MonitorSecurityBeans.class, ErrorAdvice.class, ResultAdvice.class,
        IamSecurityExceptionHandler.class, MonitorControllerTest.SecurityTestConfig.class})
class MonitorControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonitorQueryAppService queryService;

    @Test
    @WithMockUser(authorities = {
            "monitor:overview:read",
            "monitor:broker:read",
            "monitor:gateway:read",
            "monitor:connection:read",
            "monitor:diagnostic:read"
    })
    void exposesClusterReadApis() throws Exception {
        Instant queriedAt = Instant.parse("2026-08-23T01:00:00Z");
        when(queryService.overview(new OverviewQuery())).thenReturn(
                new ClusterOverviewDTO(1L, 2L, 3L, List.of(), queriedAt));
        when(queryService.brokers(new BrokerListQuery())).thenReturn(new ClusterQueryResultDTO<>(List.of(), List.of(), queriedAt));
        when(queryService.gateways(new GatewayListQuery())).thenReturn(new ClusterQueryResultDTO<>(List.of(), List.of(), queriedAt));
        when(queryService.connections(new ConnectionQuery(1L))).thenReturn(new ClusterQueryResultDTO<>(List.of(), List.of(), queriedAt));
        when(queryService.gossipRecords(new DiagnosticQuery(20))).thenReturn(new ClusterQueryResultDTO<>(List.of(), List.of(), queriedAt));
        when(queryService.migrationRecords(new DiagnosticQuery(20))).thenReturn(new ClusterQueryResultDTO<>(List.of(), List.of(), queriedAt));

        mockMvc.perform(get("/api/overview")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.connectionCount").value(3));
        mockMvc.perform(get("/api/brokers")).andExpect(status().isOk());
        mockMvc.perform(get("/api/gateways")).andExpect(status().isOk());
        mockMvc.perform(get("/api/connections").param("userId", "1")).andExpect(status().isOk());
        mockMvc.perform(get("/api/gossip/records")).andExpect(status().isOk());
        mockMvc.perform(get("/api/migrations")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {
            "monitor:connection:read",
            "monitor:diagnostic:read"
    })
    void validatesQueryParameters() throws Exception {
        mockMvc.perform(get("/api/connections")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10006));
        mockMvc.perform(get("/api/connections").param("userId", "0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10006));
        mockMvc.perform(get("/api/gossip/records").param("limit", "101")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10006));
    }

    @Test
    void rejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001));
    }

    @Test
    @WithMockUser(authorities = "monitor:broker:read")
    void rejectsAuthenticatedPrincipalWithoutOwningPermission() throws Exception {
        mockMvc.perform(get("/api/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10002));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityTestConfig {

        @Bean
        static AnnotationTemplateExpressionDefaults templateExpressionDefaults() {
            return new AnnotationTemplateExpressionDefaults();
        }

        @Bean
        SecurityFilterChain securityFilterChain(
                HttpSecurity http,
                IamSecurityExceptionHandler exceptionHandler
        ) throws Exception {
            return http
                    .authorizeHttpRequests(authorize -> authorize
                            .anyRequest().authenticated())
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(exceptionHandler)
                            .accessDeniedHandler(exceptionHandler))
                    .build();
        }
    }
}
