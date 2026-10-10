package com.co.kc.imchat.management.ops.interfaces.http;

import com.co.kc.imchat.management.iam.sdk.security.IamSecurityExceptionHandler;
import com.co.kc.imchat.management.ops.application.BrokerClusterOperationAppService;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerClusterNodeDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerClusterStateDTO;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerOperationDTO;
import com.co.kc.imchat.management.ops.infrastructure.config.beans.OpsSecurityBeans;
import com.co.kc.imchat.plugin.web.advice.ErrorAdvice;
import com.co.kc.imchat.plugin.web.advice.ResultAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrokerClusterOperationController.class)
@Import({OpsSecurityBeans.class, ErrorAdvice.class, ResultAdvice.class,
        IamSecurityExceptionHandler.class,
        BrokerClusterOperationControllerTest.SecurityTestConfig.class})
class BrokerClusterOperationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrokerClusterOperationAppService operationAppService;

    @Test
    @WithMockUser(authorities = "ops:broker:read")
    void returnsClusterStateWithoutBusinessClusterIdentity() throws Exception {
        when(operationAppService.clusterState()).thenReturn(new BrokerClusterStateDTO(
                11L, 7L, "ROUND_ROBIN", List.of(new BrokerClusterNodeDTO(
                        "broker-1", "127.0.0.1:12200", "VOTER", "ACTIVE"))));

        mockMvc.perform(get("/api/cluster-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clusterId").doesNotExist())
                .andExpect(jsonPath("$.data.revision").value(11))
                .andExpect(jsonPath("$.data.epoch").value(7))
                .andExpect(jsonPath("$.data.algorithm").value("ROUND_ROBIN"))
                .andExpect(jsonPath("$.data.brokers[0].brokerId").value("broker-1"));
    }

    @Test
    @WithMockUser(authorities = "ops:broker:cluster-state:write")
    void submitsClusterStateOperationWithOwningScope() throws Exception {
        when(operationAppService.operate(any()))
                .thenReturn(new BrokerOperationDTO("operation-1", "APPLIED",
                        7L, 8L, null, null));

        mockMvc.perform(post("/api/brokers/broker-1/cluster-state/activate")
                        .contentType("application/json")
                        .content("{\"operationId\":\"operation-1\",\"expectedEpoch\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPLIED"));
        verify(operationAppService).operate(org.mockito.ArgumentMatchers.argThat(command ->
                "ops:broker:cluster-state:write".equals(command.scope())));
    }

    @Test
    @WithMockUser(authorities = "ops:broker:read")
    void rejectsReadOnlyOperator() throws Exception {
        mockMvc.perform(post("/api/brokers/broker-1/cluster-state/activate")
                        .contentType("application/json")
                        .content("{\"operationId\":\"operation-1\",\"expectedEpoch\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10002));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class SecurityTestConfig {
        @Bean
        static AnnotationTemplateExpressionDefaults templateExpressionDefaults() {
            return new AnnotationTemplateExpressionDefaults();
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                IamSecurityExceptionHandler exceptionHandler)
                throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(exceptionHandler)
                            .accessDeniedHandler(exceptionHandler))
                    .build();
        }
    }
}
