package com.co.kc.imchat.management.iam.domain;

import com.co.kc.imchat.management.iam.domain.application.model.Application;
import com.co.kc.imchat.management.iam.domain.authorization.model.ApplicationPermission;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApp;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationPermission;
import com.co.kc.imchat.management.iam.model.io.PermissionCatalogSyncRequest;
import com.co.kc.imchat.management.iam.model.cqrs.command.ApplicationPermissionCatalogSyncCmd;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionCatalogModelTest {

    @Test
    void fullCatalogSynchronizationDoesNotExposeRevisionState() {
        assertThat(fieldNames(Application.class)).doesNotContain("catalogRevision");
        assertThat(fieldNames(ApplicationPermission.class)).doesNotContain("lastSeenRevision");
        assertThat(fieldNames(DbIamApp.class)).doesNotContain("catalogRevision");
        assertThat(fieldNames(DbIamApplicationPermission.class)).doesNotContain("lastSeenRevision");
        assertThat(componentNames(ApplicationPermissionCatalogSyncCmd.class)).doesNotContain("revision");
        assertThat(componentNames(PermissionCatalogSyncRequest.class)).doesNotContain("revision");
    }

    private String[] fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(field -> field.getName())
                .toArray(String[]::new);
    }

    private String[] componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(component -> component.getName())
                .toArray(String[]::new);
    }
}
