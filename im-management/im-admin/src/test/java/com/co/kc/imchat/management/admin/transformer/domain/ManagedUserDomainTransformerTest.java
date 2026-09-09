package com.co.kc.imchat.management.admin.transformer.domain;

import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserStatus;
import com.co.kc.imchat.management.admin.domain.user.model.ManagedUser;
import com.co.kc.imchat.service.account.admin.facade.dto.AccountUserDTO;
import com.co.kc.imchat.service.account.admin.facade.dto.AccountUserListDTO;
import com.co.kc.imchat.service.account.admin.facade.enums.AccountUserStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedUserDomainTransformerTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-26T01:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-26T02:00:00Z");

    @Test
    void shouldTransformAccountUserDetailToManagedUser() {
        AccountUserDTO source = new AccountUserDTO(
                7L, "alice", "alice@example.com", AccountUserStatus.BANNED,
                false, CREATED_AT, UPDATED_AT);

        ManagedUser user = ManagedUserDomainTransformer.INSTANCE.managedUserFrom(source);

        assertThat(user.id().value()).isEqualTo(7L);
        assertThat(user.username().value()).isEqualTo("alice");
        assertThat(user.email().value()).isEqualTo("alice@example.com");
        assertThat(user.status()).isEqualTo(ManagedUserStatus.BANNED);
        assertThat(user.deleted()).isFalse();
        assertThat(user.createdAt()).isEqualTo(CREATED_AT);
        assertThat(user.updatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void shouldTransformAccountUserListItemToManagedUser() {
        AccountUserListDTO source = new AccountUserListDTO(
                8L, "bob", "bob@example.com", AccountUserStatus.NORMAL,
                true, CREATED_AT, UPDATED_AT);

        ManagedUser user = ManagedUserDomainTransformer.INSTANCE.managedUserFrom(source);

        assertThat(user.id().value()).isEqualTo(8L);
        assertThat(user.status()).isEqualTo(ManagedUserStatus.NORMAL);
        assertThat(user.deleted()).isTrue();
    }

    @Test
    void shouldTransformManagedUserStatusToAccountStatus() {
        assertThat(ManagedUserDomainTransformer.INSTANCE.accountUserStatusFrom(ManagedUserStatus.BANNED))
                .isEqualTo(AccountUserStatus.BANNED);
    }
}
