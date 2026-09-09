package com.co.kc.imchat.management.iam.interfaces.http;

import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamAdministrator;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationAdministratorRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamOAuthAuthorization;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationRolePermission;
import com.co.kc.imchat.management.iam.model.cqrs.dto.OAuthSessionDTO;
import com.co.kc.imchat.management.iam.model.io.OAuthSessionResponse;
import com.co.kc.imchat.management.iam.model.io.ApplicationRegisterRequest;
import com.co.kc.imchat.management.iam.model.io.OAuthClientRegisterRequest;
import com.co.kc.imchat.management.iam.transformer.interfaces.OAuthSessionHttpTransformer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class IamTimeBoundaryTest {

    @Test
    void persistenceAndApplicationUseInstantWhileHttpUsesEpochMilliseconds() {
        assertFieldType(DbIamApplicationAdministratorRole.class, "createTime", Instant.class);
        assertFieldType(DbIamApplicationRolePermission.class, "createTime", Instant.class);
        Arrays.stream(DbIamOAuthAuthorization.class.getDeclaredFields())
                .filter(field -> field.getName().endsWith("At")
                        || field.getName().endsWith("Time"))
                .forEach(field -> assertThat(field.getType()).isEqualTo(Instant.class));

        assertRecordComponentType(OAuthSessionDTO.class, "createdAt", Instant.class);
        assertRecordComponentType(OAuthSessionDTO.class, "lastAccessAt", Instant.class);
        assertRecordComponentType(OAuthSessionDTO.class, "expiresAt", Instant.class);
        assertRecordComponentType(OAuthSessionResponse.class, "createdAt", Long.class);
        assertRecordComponentType(OAuthSessionResponse.class, "lastAccessAt", Long.class);
        assertRecordComponentType(OAuthSessionResponse.class, "expiresAt", Long.class);
    }

    @Test
    void createEndpointsReturnHttpResponses() throws NoSuchMethodException {
        assertThat(ApplicationController.class
                .getDeclaredMethod("registerApplication", ApplicationRegisterRequest.class)
                .getReturnType().getSimpleName()).endsWith("Response");
        assertThat(OAuthClientController.class
                .getDeclaredMethod("registerOAuthClient", OAuthClientRegisterRequest.class)
                .getReturnType().getSimpleName()).endsWith("Response");
    }

    @Test
    void mapsSessionInstantsToEpochMilliseconds() {
        OAuthSessionDTO session = new OAuthSessionDTO(
                "authorization-id",
                1L,
                "administrator",
                Instant.parse("2026-08-30T10:00:00Z"),
                Instant.parse("2026-08-30T11:00:00Z"),
                Instant.parse("2026-08-30T12:00:00Z"));

        OAuthSessionResponse response =
                OAuthSessionHttpTransformer.INSTANCE.oauthSessionResponseFrom(session);

        assertThat(response.createdAt()).isEqualTo(1788084000000L);
        assertThat(response.lastAccessAt()).isEqualTo(1788087600000L);
        assertThat(response.expiresAt()).isEqualTo(1788091200000L);
    }

    private void assertFieldType(Class<?> owner, String name, Class<?> expectedType) {
        Class<?> currentType = owner;
        while (currentType != null) {
            try {
                Field field = currentType.getDeclaredField(name);
                assertThat(field.getType()).isEqualTo(expectedType);
                return;
            } catch (NoSuchFieldException ignored) {
                currentType = currentType.getSuperclass();
            }
        }
        throw new AssertionError("Field not found: " + owner.getName() + "." + name);
    }

    private void assertRecordComponentType(
            Class<?> owner,
            String name,
            Class<?> expectedType
    ) {
        RecordComponent component = Arrays.stream(owner.getRecordComponents())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow();
        assertThat(component.getType()).isEqualTo(expectedType);
    }
}
