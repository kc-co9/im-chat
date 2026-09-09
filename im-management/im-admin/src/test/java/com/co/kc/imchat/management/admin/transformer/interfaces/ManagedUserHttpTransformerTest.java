package com.co.kc.imchat.management.admin.transformer.interfaces;

import com.co.kc.imchat.management.admin.domain.user.model.ManagedUserStatus;
import com.co.kc.imchat.management.admin.model.cqrs.dto.ManagedUserDTO;
import com.co.kc.imchat.management.admin.model.io.ManagedUserResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedUserHttpTransformerTest {

    @Test
    void convertsAbsoluteTimesToEpochMilliseconds() {
        Instant createdAt = Instant.parse("2026-08-24T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-08-24T01:00:00Z");
        ManagedUserDTO user = new ManagedUserDTO(
                1L, "user", "user@example.com", ManagedUserStatus.NORMAL,
                false, createdAt, updatedAt);

        ManagedUserResponse response =
                ManagedUserHttpTransformer.INSTANCE.responseFrom(user);

        assertThat(response.createdAt()).isEqualTo(createdAt.toEpochMilli());
        assertThat(response.updatedAt()).isEqualTo(updatedAt.toEpochMilli());
    }
}
