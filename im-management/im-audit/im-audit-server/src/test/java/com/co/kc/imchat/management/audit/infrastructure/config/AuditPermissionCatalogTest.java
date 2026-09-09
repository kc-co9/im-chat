package com.co.kc.imchat.management.audit.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditPermissionCatalogTest {

    @Test
    void declaresOnlyReadAndExportPermissions() {
        AuditPermissionCatalog catalog = new AuditPermissionCatalog();

        assertThat(catalog.permissions())
                .extracting(permission -> permission.code())
                .containsExactly("audit:read", "audit:export");
    }
}
