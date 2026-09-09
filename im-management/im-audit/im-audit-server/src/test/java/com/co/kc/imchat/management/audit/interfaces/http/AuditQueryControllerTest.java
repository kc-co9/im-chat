package com.co.kc.imchat.management.audit.interfaces.http;

import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.audit.application.AuditQueryAppService;
import com.co.kc.imchat.management.audit.domain.model.AuditOutcome;
import com.co.kc.imchat.management.audit.domain.model.AuditType;
import com.co.kc.imchat.management.audit.model.cqrs.query.AuditPageQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditQueryControllerTest {

    @Test
    void bindsQueryParametersIntoPageRequestAndApplicationQuery() throws Exception {
        AuditQueryAppService appService = mock(AuditQueryAppService.class);
        when(appService.pageAudit(any())).thenAnswer(invocation -> {
            AuditPageQuery query = invocation.getArgument(0);
            return PagingResult.empty(query.paging());
        });
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AuditQueryController(appService))
                .build();

        mockMvc.perform(get("/api/audits")
                        .param("sourceApp", "imAdmin")
                        .param("type", "BUSINESS")
                        .param("outcome", "SUCCESS")
                        .param("occurredFrom", "1785542400000")
                        .param("occurredTo", "1785628800000"))
                .andExpect(status().isOk());

        ArgumentCaptor<AuditPageQuery> queryCaptor =
                ArgumentCaptor.forClass(AuditPageQuery.class);
        verify(appService).pageAudit(queryCaptor.capture());
        AuditPageQuery query = queryCaptor.getValue();
        assertThat(query.paging().pageNo()).isEqualTo(1);
        assertThat(query.paging().pageSize()).isEqualTo(20);
        assertThat(query.type()).isEqualTo(AuditType.BUSINESS);
        assertThat(query.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(query.occurredFrom()).isEqualTo(
                java.time.Instant.ofEpochMilli(1_785_542_400_000L));
    }
}
