package com.co.kc.imchat.management.iam.infrastructure.mybatis;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamAdministrator;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationAdministratorRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApp;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamOAuthAuthorization;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationPermission;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationRolePermission;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamInternalAdministratorRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamInternalRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamOAuthClient;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.DbIamOAuthClientStatus;
import com.co.kc.imchat.plugin.datasource.dao.BaseEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IamPersistenceMappingTest {

    @Test
    void mapsEveryIamEntityToItsOwnedTable() {
        Map<Class<?>, String> mappings = Map.of(
                DbIamAdministrator.class, "db_iam_administrator",
                DbIamApp.class, "db_iam_app",
                DbIamApplicationPermission.class, "db_iam_application_permission",
                DbIamApplicationRole.class, "db_iam_application_role",
                DbIamApplicationAdministratorRole.class, "db_iam_application_administrator_role",
                DbIamApplicationRolePermission.class, "db_iam_application_role_permission",
                DbIamInternalRole.class, "db_iam_internal_role",
                DbIamInternalAdministratorRole.class, "db_iam_internal_administrator_role",
                DbIamOAuthAuthorization.class, "db_iam_authorization");

        mappings.forEach((type, table) -> assertThat(type.getAnnotation(TableName.class).value())
                .isEqualTo(table));
    }

    @Test
    void usesWrapperTypesForNullablePersistenceBoundaries() {
        assertThat(Map.of(
                DbIamAdministrator.class, DbIamAdministrator.class.getDeclaredFields(),
                DbIamApp.class, DbIamApp.class.getDeclaredFields(),
                DbIamApplicationPermission.class, DbIamApplicationPermission.class.getDeclaredFields(),
                DbIamApplicationRole.class, DbIamApplicationRole.class.getDeclaredFields(),
                DbIamApplicationAdministratorRole.class, DbIamApplicationAdministratorRole.class.getDeclaredFields(),
                DbIamApplicationRolePermission.class, DbIamApplicationRolePermission.class.getDeclaredFields(),
                DbIamInternalRole.class, DbIamInternalRole.class.getDeclaredFields(),
                DbIamInternalAdministratorRole.class, DbIamInternalAdministratorRole.class.getDeclaredFields(),
                DbIamOAuthAuthorization.class, DbIamOAuthAuthorization.class.getDeclaredFields()))
                .allSatisfy((type, fields) -> assertThat(Arrays.stream(fields)
                        .filter(field -> !Modifier.isStatic(field.getModifiers()))
                        .map(Field::getType)
                        .filter(Class::isPrimitive))
                        .as(type.getSimpleName())
                        .isEmpty());
    }

    @Test
    void everyIamEntityUsesTheStandardPersistenceBaseClass() {
        assertThat(List.of(
                DbIamAdministrator.class,
                DbIamApp.class,
                DbIamOAuthClient.class,
                DbIamApplicationPermission.class,
                DbIamApplicationRole.class,
                DbIamApplicationAdministratorRole.class,
                DbIamApplicationRolePermission.class,
                DbIamInternalRole.class,
                DbIamInternalAdministratorRole.class,
                DbIamOAuthAuthorization.class))
                .allMatch(BaseEntity.class::isAssignableFrom);
    }

    @Test
    void authorizationEntityExposesOnlyTokenDigests() {
        assertThat(Arrays.stream(DbIamOAuthAuthorization.class.getDeclaredFields())
                .map(Field::getName))
                .contains("authorizationCodeDigest", "accessTokenDigest", "refreshTokenDigest")
                .doesNotContain("authorizationCode", "accessToken", "refreshToken");
    }

    @Test
    void mapsOAuthClientStatusToNumericDatabaseValue() {
        assertThat(Arrays.stream(DbIamOAuthClientStatus.class.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(EnumValue.class)))
                .singleElement()
                .extracting(Field::getType)
                .isEqualTo(Integer.TYPE);
    }

    @Test
    void keepsOAuthClientRedirectJsonAsRawStrings() throws NoSuchFieldException {
        TableName tableName = DbIamOAuthClient.class.getAnnotation(TableName.class);
        Field redirectUris = DbIamOAuthClient.class.getDeclaredField("redirectUris");
        Field postLogoutRedirectUris = DbIamOAuthClient.class.getDeclaredField("postLogoutRedirectUris");

        assertThat(tableName.autoResultMap()).isFalse();
        assertThat(redirectUris.getType()).isEqualTo(String.class);
        assertThat(postLogoutRedirectUris.getType()).isEqualTo(String.class);
        assertThat(redirectUris.getAnnotation(TableField.class)).isNull();
        assertThat(postLogoutRedirectUris.getAnnotation(TableField.class)).isNull();
    }

    @Test
    void mapsAuthorizationClaimsAsStructuredJson() throws NoSuchFieldException {
        TableName tableName = DbIamOAuthAuthorization.class.getAnnotation(TableName.class);
        Field accessTokenClaims = DbIamOAuthAuthorization.class.getDeclaredField("accessTokenClaims");
        Field idTokenClaims = DbIamOAuthAuthorization.class.getDeclaredField("idTokenClaims");

        assertThat(tableName.autoResultMap()).isTrue();
        assertThat(accessTokenClaims.getType()).isEqualTo(Map.class);
        assertThat(idTokenClaims.getType()).isEqualTo(Map.class);
        assertThat(accessTokenClaims.getAnnotation(TableField.class).typeHandler())
                .isEqualTo(JacksonTypeHandler.class);
        assertThat(idTokenClaims.getAnnotation(TableField.class).typeHandler())
                .isEqualTo(JacksonTypeHandler.class);
    }

    @Test
    void doesNotExposeCredentialDigestsThroughEntityToString() {
        DbIamOAuthClient client = new DbIamOAuthClient();
        client.setClientSecretHash("client-secret-hash");
        DbIamAdministrator administrator = new DbIamAdministrator();
        administrator.setPasswordHash("administrator-password-hash");
        DbIamOAuthAuthorization authorization = new DbIamOAuthAuthorization();
        authorization.setAccessTokenDigest("access-token-digest");

        assertThat(client.toString()).doesNotContain("client-secret-hash");
        assertThat(administrator.toString()).doesNotContain("administrator-password-hash");
        assertThat(authorization.toString()).doesNotContain("access-token-digest");
    }
}
