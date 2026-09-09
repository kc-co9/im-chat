package com.co.kc.imchat.management.iam.domain;

import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ApplicationRoleTypeNamingTest {

    @Test
    void roleClassificationUsesTypeVocabulary() throws NoSuchFieldException {
        String modelPackage = "com.co.kc.imchat.management.iam.domain.authorization.model.";
        String persistencePackage = "com.co.kc.imchat.management.iam.infrastructure.mybatis.enums.";

        assertThatCode(() -> Class.forName(modelPackage + "ApplicationRoleType"))
                .doesNotThrowAnyException();
        assertThatCode(() -> Class.forName(persistencePackage + "DbIamApplicationRoleType"))
                .doesNotThrowAnyException();
        assertThat(ApplicationRole.class.getDeclaredField("type").getType().getSimpleName())
                .isEqualTo("ApplicationRoleType");
        assertThat(DbIamApplicationRole.class.getDeclaredField("type").getType().getSimpleName())
                .isEqualTo("DbIamApplicationRoleType");
    }
}
