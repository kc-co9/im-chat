package com.co.kc.imchat.service.account.facade;

import com.co.kc.imchat.service.account.facade.dto.SessionAuthDTO;
import com.co.kc.imchat.service.account.facade.params.AccessTokenParams;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AuthenticationContractTest {

    @Test
    void accessTokenParamsRoundTripPreservesToken() throws Exception {
        AccessTokenParams params = new AccessTokenParams("access-token");

        AccessTokenParams restored = roundTrip(params);

        assertThat(restored).isEqualTo(params);
        assertThat(restored.token()).isEqualTo("access-token");
    }

    @Test
    void accessTokenParamsRejectBlankToken() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AccessTokenParams("  "))
                .withMessage("token must not be blank");
    }

    @Test
    void accessTokenParamsRejectMissingToken() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AccessTokenParams(null))
                .withMessage("token must not be blank");
    }

    @Test
    void authenticatedResultRoundTripPreservesTrustedSessionFields() throws Exception {
        Instant expiresAt = Instant.parse("2026-08-14T10:15:30Z");
        SessionAuthDTO result = new SessionAuthDTO(42L, "session-v2", expiresAt);

        SessionAuthDTO restored = roundTrip(result);

        assertThat(restored).isEqualTo(result);
        assertThat(restored.userId()).isEqualTo(42L);
        assertThat(restored.sessionVersion()).isEqualTo("session-v2");
        assertThat(restored.accessTokenExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void authenticationResultExposesOnlyApprovedRecordComponents() {
        assertThat(Arrays.stream(SessionAuthDTO.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly("userId", "sessionVersion", "accessTokenExpiresAt")
                .noneMatch(name -> name.toLowerCase().contains("refresh") || name.toLowerCase().contains("digest"));
    }

    @Test
    void accountServiceExposesConciseAuthenticationOperation() throws Exception {
        assertThat(AccountService.class.getMethod("authenticate", AccessTokenParams.class).getReturnType())
                .isEqualTo(SessionAuthDTO.class);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Serializable> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
