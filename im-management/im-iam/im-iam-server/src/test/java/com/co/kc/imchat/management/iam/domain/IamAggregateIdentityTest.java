package com.co.kc.imchat.management.iam.domain;

import com.co.kc.imchat.common.domain.shared.model.Identification;
import com.co.kc.imchat.management.iam.domain.administrator.model.Administrator;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorEmail;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorId;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorPassword;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorStatus;
import com.co.kc.imchat.management.iam.domain.administrator.model.AdministratorUsername;
import com.co.kc.imchat.management.iam.domain.application.model.Application;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRole;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleCode;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleId;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleType;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleName;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRoleStatus;
import com.co.kc.imchat.management.iam.domain.application.model.AppKey;
import com.co.kc.imchat.management.iam.domain.application.model.AppId;
import com.co.kc.imchat.management.iam.domain.application.model.AppName;
import com.co.kc.imchat.management.iam.domain.application.model.AppStatus;
import com.co.kc.imchat.management.iam.domain.application.model.OAuthClient;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IamAggregateIdentityTest {

    @Test
    void declaredAggregateRootsExcludeSharedIdentityFromGeneratedEquality() {
        Administrator active = administrator(AdministratorStatus.ACTIVE);
        Administrator disabled = administrator(AdministratorStatus.DISABLED);

        assertThat(active).isInstanceOf(Identification.class).isNotEqualTo(disabled);
        assertThat(application()).isInstanceOf(Identification.class);
        assertThat(OAuthClient.class.getSuperclass()).isEqualTo(Identification.class);
        assertThat(role()).isInstanceOf(Identification.class);
        assertThat(declaredMethodNames(Administrator.class))
                .contains("equals", "hashCode");
        assertThat(declaredMethodNames(Application.class))
                .contains("equals", "hashCode");
        assertThat(declaredMethodNames(ApplicationRole.class))
                .contains("equals", "hashCode");
    }

    @Test
    void technicalPrimaryKeyIsRestoredOnlyAfterDomainConstruction() {
        Administrator administrator = Administrator.builder()
                .id(new AdministratorId(7L))
                .username(new AdministratorUsername("admin"))
                .email(new AdministratorEmail("admin@example.com"))
                .password(new AdministratorPassword("digest"))
                .status(AdministratorStatus.ACTIVE)
                .build();
        administrator.setPkId(99L);

        assertThat(administrator.getPkId()).isEqualTo(99L);
    }

    @Test
    void aggregateBuildersDoNotExposePersistencePrimaryKey() {
        assertThat(declaredMethodNames(Administrator.builder().getClass()))
                .doesNotContain("pkId");
        assertThat(declaredMethodNames(OAuthClient.builder().getClass()))
                .doesNotContain("pkId");
        assertThat(declaredMethodNames(ApplicationRole.builder().getClass()))
                .doesNotContain("pkId");
    }

    @Test
    void aggregateBuildersValidateRequiredDomainState() {
        assertThatThrownBy(() -> Administrator.builder().build())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OAuthClient.builder().build())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ApplicationRole.builder().build())
                .isInstanceOf(IllegalStateException.class);
    }

    private Administrator administrator(AdministratorStatus status) {
        return Administrator.builder()
                .id(new AdministratorId(7L))
                .username(new AdministratorUsername("admin"))
                .email(new AdministratorEmail("admin@example.com"))
                .password(new AdministratorPassword("digest"))
                .status(status)
                .build();
    }

    private Application application() {
        return new Application(new AppId(11L), new AppKey("imAdmin"),
                new AppName("Admin"), AppStatus.ACTIVE);
    }

    private ApplicationRole role() {
        return ApplicationRole.builder()
                .id(new ApplicationRoleId(21L))
                .appId(new AppId(11L))
                .code(new ApplicationRoleCode("AUDITOR"))
                .name(new ApplicationRoleName("审计员"))
                .type(ApplicationRoleType.CUSTOM)
                .status(ApplicationRoleStatus.ACTIVE)
                .permissionIds(Set.of())
                .build();
    }

    private Set<String> declaredMethodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(java.util.stream.Collectors.toSet());
    }
}
