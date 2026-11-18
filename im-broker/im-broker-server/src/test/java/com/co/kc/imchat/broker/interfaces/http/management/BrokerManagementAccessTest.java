package com.co.kc.imchat.broker.interfaces.http.management;

import com.co.kc.imchat.broker.model.cqrs.result.ActivateBrokerResult;
import com.co.kc.imchat.broker.model.cqrs.result.AddLearnerResult;
import com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult.Status;
import com.co.kc.imchat.broker.model.cqrs.result.ForceDeactivateBrokerResult;
import com.co.kc.imchat.broker.infrastructure.config.properties.BrokerManagementProperties;
import com.co.kc.imchat.broker.application.cluster.BrokerClusterAppService;
import com.co.kc.imchat.broker.application.cluster.BrokerMembershipAppService;
import com.co.kc.imchat.plugin.web.advice.ErrorAdvice;
import com.co.kc.imchat.plugin.web.advice.ResultAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({BrokerRoutingController.class,
        BrokerMembershipController.class})
@Import({ErrorAdvice.class, ResultAdvice.class,
        BrokerManagementAccessTest.AuthorizationTestConfiguration.class})
@EnableConfigurationProperties(BrokerManagementProperties.class)
class BrokerManagementAccessTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrokerClusterAppService operationService;
    @MockitoBean
    private BrokerMembershipAppService membershipService;

    @Test
    void rejectsMissingScopeAndWrongAudience() throws Exception {
        mockMvc.perform(request("activate").header("X-Im-Audience", "wrong")
                .header("X-Im-Scopes", "ops:broker:cluster-state:write")
        ).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10000));
        mockMvc.perform(request("activate")
                .header("X-Im-Audience", "im-broker-management")
                .header("X-Im-Scopes", "ops:broker:read")
        ).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10000));
        verify(operationService, never()).activate(any());
    }

    @Test
    void activationReturnsApplicationResult() throws Exception {
        when(operationService.activate(any())).thenReturn(CompletableFuture.completedFuture(
                new ActivateBrokerResult("operation-1", "APPLIED",
                        11L, 12L, null, "committed")));

        MvcResult activation = mockMvc.perform(authorized(request("activate")))
                .andReturn();
        mockMvc.perform(asyncDispatch(activation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.acceptedEpoch").value(11));
        verify(operationService).activate(any());
    }

    @Test
    void returnsLeaderEndpointWithoutProxyingFollowerResult() throws Exception {
        when(operationService.activate(any())).thenReturn(CompletableFuture.completedFuture(
                new ActivateBrokerResult("operation-1", "NOT_LEADER",
                        11L, null, "http://broker-2:19020", "not leader")));

        MvcResult follower = mockMvc.perform(authorized(request("activate")))
                .andReturn();
        mockMvc.perform(asyncDispatch(follower))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NOT_LEADER"))
                .andExpect(jsonPath("$.data.leaderEndpoint").value("http://broker-2:19020"))
                .andExpect(jsonPath("$.data.leaderHint").doesNotExist());
    }

    @Test
    void requiresDedicatedScopesForRaftAndForceOperations() throws Exception {
        when(membershipService.addLearner(any())).thenReturn(CompletableFuture.completedFuture(
                new AddLearnerResult("operation-1", Status.APPLIED, null, null)));
        when(operationService.forceDeactivate(any())).thenReturn(
                CompletableFuture.completedFuture(
                        new ForceDeactivateBrokerResult("operation-1", "ACCEPTED",
                                11L, 12L, null, null)));

        MvcResult raft = mockMvc.perform(authorized(
                request("add-learner"), "ops:broker:raft:write")).andReturn();
        mockMvc.perform(asyncDispatch(raft)).andExpect(status().isOk());

        MvcResult force = mockMvc.perform(authorized(
                request("force-deactivate"), "ops:broker:force")).andReturn();
        mockMvc.perform(asyncDispatch(force)).andExpect(status().isOk());

        verify(membershipService).addLearner(any());
        verify(operationService).forceDeactivate(any());

        mockMvc.perform(authorized(request("add-learner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10000));
        mockMvc.perform(authorized(request("force-deactivate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10000));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String action) {
        return post("/management/cluster-state/brokers/broker-1/" + action)
                .header("Authorization", "Bearer local-ops-management-token")
                .header("Idempotency-Key", "operation-1")
                .header("If-Match-Epoch", "11")
                .contentType("application/json")
                .content("add-learner".equals(action)
                        ? "{\"membershipEndpoint\":\"127.0.0.1:19001\"}"
                        : "{}");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorized(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return authorized(request, "ops:broker:cluster-state:write");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorized(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String scope) {
        return request.header("X-Im-Audience", "im-broker-management")
                .header("X-Im-Scopes", scope);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuthorizationTestConfiguration {

    }
}
