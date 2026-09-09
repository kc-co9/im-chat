package com.co.kc.imchat.service.account.transformer.interfaces;

import com.co.kc.imchat.common.model.page.Paging;
import com.co.kc.imchat.common.model.page.PagingResult;
import com.co.kc.imchat.service.account.admin.facade.dto.AccountUserListDTO;
import com.co.kc.imchat.service.account.admin.facade.enums.AccountUserStatus;
import com.co.kc.imchat.service.account.admin.facade.params.UserPageParams;
import com.co.kc.imchat.service.account.domain.user.model.UserStatus;
import com.co.kc.imchat.service.account.model.cqrs.dto.ManagedUserListDTO;
import com.co.kc.imchat.service.account.model.cqrs.query.ManagedUserPageQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccountAdminRpcTransformerTest {

    @Test
    void mapsFacadePageNumberAndStatusToCqrsBoundaryValues() {
        ManagedUserPageQuery query = AccountAdminRpcTransformer.INSTANCE.managedUserPageQueryFrom(
                new UserPageParams(new Paging(2, 20), 1001L, "alice", "alice@example.com",
                        AccountUserStatus.BANNED));

        assertThat(query.paging().pageNo()).isEqualTo(2);
        assertThat(query.paging().pageSize()).isEqualTo(20);
        assertThat(query.userId()).isEqualTo(1001L);
        assertThat(query.status()).isEqualTo("BANNED");
    }

    @Test
    void mapsApplicationPageToFacadeListProjection() {
        Instant now = Instant.parse("2026-08-26T00:00:00Z");
        PagingResult<ManagedUserListDTO> source = new PagingResult<>(
                new Paging(2, 20),
                List.of(new ManagedUserListDTO(
                        1001L, "alice", "alice@example.com", UserStatus.NORMAL,
                        false, now, now)),
                21L);

        PagingResult<AccountUserListDTO> result =
                AccountAdminRpcTransformer.INSTANCE.accountUserPageFrom(source);

        assertThat(result.paging()).isEqualTo(source.paging());
        assertThat(result.total()).isEqualTo(21L);
        assertThat(result.records()).singleElement().satisfies(user -> {
            assertThat(user.userId()).isEqualTo(1001L);
            assertThat(user.status()).isEqualTo(AccountUserStatus.NORMAL);
        });
    }
}
