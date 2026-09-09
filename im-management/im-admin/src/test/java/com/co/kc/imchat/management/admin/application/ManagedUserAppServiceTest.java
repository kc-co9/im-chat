package com.co.kc.imchat.management.admin.application;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.management.admin.adapter.AccountAdminAdapter;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUser;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserEmail;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserId;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserName;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserQueryCondition;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserStatus;
import com.co.kc.imchat.management.admin.model.cqrs.query.ManagedUserPageQuery;
import com.co.kc.imchat.management.admin.model.cqrs.dto.ManagedUserDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagedUserAppServiceTest {

    @Test
    void passesPagingAndFilterConditionSeparately() {
        AccountAdminAdapter adapter = mock(AccountAdminAdapter.class);
        Paging paging = new Paging(2, 20);
        PagingResult<ManagedUser> expected = new PagingResult<>(paging, List.of(), 0L);
        when(adapter.pageUsers(eq(paging), any(ManagedUserQueryCondition.class)))
                .thenReturn(expected);
        ManagedUserAppService service = new ManagedUserAppService(adapter);

        PagingResult<ManagedUserDTO> result = service.page(new ManagedUserPageQuery(
                paging, 7L, "alice", "alice@example.com", ManagedUserStatus.BANNED.name()));

        assertThat(result.paging()).isEqualTo(expected.paging());
        assertThat(result.records()).isEmpty();
        assertThat(result.total()).isEqualTo(expected.total());
        ArgumentCaptor<ManagedUserQueryCondition> conditionCaptor =
                ArgumentCaptor.forClass(ManagedUserQueryCondition.class);
        verify(adapter).pageUsers(eq(paging), conditionCaptor.capture());
        assertThat(conditionCaptor.getValue()).satisfies(condition -> {
            assertThat(condition.userId()).contains(new ManagedUserId(7L));
            assertThat(condition.username()).contains(new ManagedUserName("alice"));
            assertThat(condition.email()).contains(new ManagedUserEmail("alice@example.com"));
            assertThat(condition.status()).contains(ManagedUserStatus.BANNED);
        });
    }

    @Test
    void treatsBlankTextFiltersAsAbsent() {
        AccountAdminAdapter adapter = mock(AccountAdminAdapter.class);
        Paging paging = new Paging(1, 20);
        when(adapter.pageUsers(eq(paging), any(ManagedUserQueryCondition.class)))
                .thenReturn(new PagingResult<>(paging, List.of(), 0L));
        ManagedUserAppService service = new ManagedUserAppService(adapter);

        service.page(new ManagedUserPageQuery(paging, null, "", " ", ""));

        ArgumentCaptor<ManagedUserQueryCondition> conditionCaptor =
                ArgumentCaptor.forClass(ManagedUserQueryCondition.class);
        verify(adapter).pageUsers(eq(paging), conditionCaptor.capture());
        assertThat(conditionCaptor.getValue()).satisfies(condition -> {
            assertThat(condition.userId()).isEmpty();
            assertThat(condition.username()).isEmpty();
            assertThat(condition.email()).isEmpty();
            assertThat(condition.status()).isEmpty();
        });
    }

}
