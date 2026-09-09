package com.co.kc.imchat.management.iam.domain.administrator.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdministratorTest {

    @Test
    void activeAdministratorCanSignIn() {
        Administrator administrator = activeAdministrator();

        assertThat(administrator.isActive()).isTrue();
    }

    @Test
    void disabledAdministratorCannotSignInUntilEnabled() {
        Administrator administrator = activeAdministrator();
        administrator.disable();

        assertThat(administrator.isActive()).isFalse();

        administrator.enable();

        assertThat(administrator.isActive()).isTrue();
    }

    private static Administrator activeAdministrator() {
        return Administrator.builder()
                .id(new AdministratorId(1L))
                .username(new AdministratorUsername("admin"))
                .email(new AdministratorEmail("admin@example.com"))
                .password(new AdministratorPassword("bcrypt-hash"))
                .status(AdministratorStatus.ACTIVE)
                .build();
    }
}
