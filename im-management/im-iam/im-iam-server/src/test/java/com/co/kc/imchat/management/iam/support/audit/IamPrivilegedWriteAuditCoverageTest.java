package com.co.kc.imchat.management.iam.support.audit;

import com.co.kc.imchat.management.iam.interfaces.http.ApplicationPermissionCatalogController;
import com.co.kc.imchat.management.iam.interfaces.http.ApplicationPermissionController;
import com.co.kc.imchat.management.iam.interfaces.http.ApplicationRoleController;
import com.co.kc.imchat.management.iam.interfaces.http.AdministratorController;
import com.co.kc.imchat.management.iam.interfaces.http.ApplicationController;
import com.co.kc.imchat.management.iam.interfaces.http.OAuthClientController;
import com.co.kc.imchat.management.iam.interfaces.http.OAuthSessionController;
import com.co.kc.imchat.management.audit.sdk.annotation.Audited;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IamPrivilegedWriteAuditCoverageTest {

    @Test
    void everyPrivilegedHttpWriteDeclaresSecurityAudit() {
        List<Method> unauditedWrites = List.of(
                        AdministratorController.class,
                        ApplicationController.class,
                        OAuthClientController.class,
                        ApplicationRoleController.class,
                        ApplicationPermissionController.class,
                        OAuthSessionController.class,
                        ApplicationPermissionCatalogController.class).stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(IamPrivilegedWriteAuditCoverageTest::isHttpWrite)
                .filter(method -> !AnnotatedElementUtils.hasAnnotation(
                        method, Audited.class))
                .toList();

        assertThat(unauditedWrites)
                .as("IAM privileged HTTP writes must declare @Audited")
                .isEmpty();
    }

    private static boolean isHttpWrite(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, PostMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PutMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PatchMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, DeleteMapping.class);
    }
}
