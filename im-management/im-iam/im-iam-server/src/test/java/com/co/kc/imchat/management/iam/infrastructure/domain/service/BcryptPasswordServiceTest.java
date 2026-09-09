package com.co.kc.imchat.management.iam.infrastructure.domain.service;

import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorPassword;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorRawPassword;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class BcryptPasswordServiceTest {

    @Test
    void encodesAndVerifiesAdministratorPassword() {
        BcryptPasswordService service = new BcryptPasswordService();
        AdministratorRawPassword password =
                new AdministratorRawPassword("strong-password");

        AdministratorPassword encoded = service.encrypt(password);

        assertThat(encoded.value()).isNotEqualTo(password.value());
        assertThat(service.verify(password, encoded)).isTrue();
        assertThat(service.verify(
                new AdministratorRawPassword("another-password"), encoded))
                .isFalse();
    }

    @Test
    void verifiesUnknownAccountAgainstDummyHash() {
        BcryptPasswordService service = new BcryptPasswordService();

        assertThatCode(() -> service.verifyUnknown(
                new AdministratorRawPassword("untrusted-password")))
                .doesNotThrowAnyException();
    }
}
