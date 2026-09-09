package com.co.kc.imchat.management.audit.application;

import com.co.kc.imchat.common.exception.NotFoundException;
import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.audit.domain.model.AuditId;
import com.co.kc.imchat.management.audit.domain.model.AuditOutcome;
import com.co.kc.imchat.management.audit.domain.model.AuditType;
import com.co.kc.imchat.management.audit.domain.repository.AuditEventRepository;
import com.co.kc.imchat.management.audit.model.cqrs.query.AuditDetailsQuery;
import com.co.kc.imchat.management.audit.model.cqrs.query.AuditPageQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditQueryAppServiceTest {

    @Test
    void pagesWithBoundedTypedCondition() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        when(repository.page(any(), any())).thenReturn(
                new PagingResult<>(new Paging(1, 20), List.of(), 0L));
        AuditQueryAppService service = new AuditQueryAppService(repository);
        AuditPageQuery query = new AuditPageQuery(
                new Paging(1, 20),
                "imAdmin",
                AuditType.BUSINESS,
                "USER_BAN",
                AuditOutcome.SUCCESS,
                "1001",
                "USER",
                "2001",
                "trace-1",
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-28T00:00:00Z"));

        assertThat(service.pageAudit(query).records()).isEmpty();
    }

    @Test
    void reportsMissingBusinessAuditIdentity() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        when(repository.find(new AuditId("missing"))).thenReturn(Optional.empty());
        AuditQueryAppService service = new AuditQueryAppService(repository);

        assertThatThrownBy(() -> service.getAuditDetail(new AuditDetailsQuery("missing")))
                .isInstanceOf(NotFoundException.class);
    }
}
