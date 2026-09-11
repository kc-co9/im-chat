package com.co.kc.imchat.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ModuleBoundaryTest {
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .importPackages("com.co.kc.imchat");

    @Test
    void commonMustNotDependOnRuntimeModules() {
        noClasses()
                .that().resideInAPackage("com.co.kc.imchat.common..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.co.kc.imchat.broker..",
                        "com.co.kc.imchat.gateway..",
                        "com.co.kc.imchat.management..",
                        "com.co.kc.imchat.plugin..",
                        "com.co.kc.imchat.service..")
                .check(CLASSES);
    }

    @Test
    void pluginsMustNotDependOnRuntimeModules() {
        noClasses()
                .that().resideInAPackage("com.co.kc.imchat.plugin..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.co.kc.imchat.broker..",
                        "com.co.kc.imchat.gateway..",
                        "com.co.kc.imchat.management..",
                        "com.co.kc.imchat.service..")
                .check(CLASSES);
    }

    @Test
    void webPluginMustNotDependOnSessionOrManagementIdentity() {
        noClasses()
                .that().resideInAPackage("com.co.kc.imchat.plugin.web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.co.kc.imchat.plugin.session..",
                        "com.co.kc.imchat.management.iam..",
                        "com.co.kc.imchat.management.admin..",
                        "com.co.kc.imchat.management.monitor..")
                .check(CLASSES);
    }

    @Test
    void monitorMustNotDependOnBrokerServerImplementations() {
        JavaClasses monitorClasses = new ClassFileImporter().importPath(repoRoot().resolve(
                "im-management/im-monitor/target/classes"));
        noClasses()
                .that().resideInAPackage("com.co.kc.imchat.management.monitor..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.co.kc.imchat.broker.config..",
                        "com.co.kc.imchat.broker.diagnostic..",
                        "com.co.kc.imchat.broker.domain..",
                        "com.co.kc.imchat.broker.interfaces..",
                        "com.co.kc.imchat.broker.lifecycle..",
                        "com.co.kc.imchat.broker.support..")
                .check(monitorClasses);
    }

    @Test
    void iamSdkMustNotDependOnIamServerImplementation() {
        noClasses()
                .that().resideInAPackage("com.co.kc.imchat.management.iam.sdk..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.co.kc.imchat.management.iam.application..",
                        "com.co.kc.imchat.management.iam.domain..",
                        "com.co.kc.imchat.management.iam.infrastructure..",
                        "com.co.kc.imchat.management.iam.interfaces..")
                .check(CLASSES);
    }

    @Test
    void auditSdkMustNotDependOnAuditServerImplementation() {
        noClasses()
                .that().resideInAPackage("com.co.kc.imchat.management.audit.sdk..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.co.kc.imchat.management.audit.application..",
                        "com.co.kc.imchat.management.audit.domain..",
                        "com.co.kc.imchat.management.audit.infrastructure..",
                        "com.co.kc.imchat.management.audit.interfaces..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void auditServerMustNotDependOnOtherManagementImplementations() {
        noClasses()
                .that().resideInAPackage("com.co.kc.imchat.management.audit..")
                .and().resideOutsideOfPackage("com.co.kc.imchat.management.audit.sdk..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.co.kc.imchat.management.admin..",
                        "com.co.kc.imchat.management.monitor..",
                        "com.co.kc.imchat.management.iam.application..",
                        "com.co.kc.imchat.management.iam.domain..",
                        "com.co.kc.imchat.management.iam.infrastructure..",
                        "com.co.kc.imchat.management.iam.interfaces..")
                .check(CLASSES);
    }

    @Test
    void auditProducersMustNotDependOnAuditServerImplementation() {
        noClasses()
                .that().resideInAnyPackage(
                        "com.co.kc.imchat.management.admin..",
                        "com.co.kc.imchat.management.iam..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.co.kc.imchat.management.audit.application..",
                        "com.co.kc.imchat.management.audit.domain..",
                        "com.co.kc.imchat.management.audit.infrastructure..",
                        "com.co.kc.imchat.management.audit.interfaces..")
                .check(CLASSES);
    }

    @Test
    void iamServerMustNotDependOnManagementApplications() {
        JavaClasses iamServerClasses = new ClassFileImporter().importPath(repoRoot().resolve(
                "im-management/im-iam/im-iam-server/target/classes"));
        noClasses()
                .that().resideInAnyPackage(
                        "com.co.kc.imchat.management.iam.application..",
                        "com.co.kc.imchat.management.iam.domain..",
                        "com.co.kc.imchat.management.iam.infrastructure..",
                        "com.co.kc.imchat.management.iam.interfaces..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.co.kc.imchat.management.admin..",
                        "com.co.kc.imchat.management.monitor..")
                .check(iamServerClasses);
    }

    @Test
    void publicContractsMustNotDependOnServerImplementations() {
        noClasses()
                .that().resideInAnyPackage(
                        "com.co.kc.imchat.broker.sdk..",
                        "com.co.kc.imchat.gateway.ws.sdk..",
                        "com.co.kc.imchat.service.account.facade..",
                        "com.co.kc.imchat.service.account.admin.facade..",
                        "com.co.kc.imchat.service.social.facade..",
                        "com.co.kc.imchat.service.message.facade..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.co.kc.imchat.broker.domain..",
                        "com.co.kc.imchat.broker.interfaces..",
                        "com.co.kc.imchat.gateway.ws.handler..",
                        "com.co.kc.imchat.gateway.ws.registry..",
                        "com.co.kc.imchat.gateway.ws.server..",
                        "com.co.kc.imchat.service..application..",
                        "com.co.kc.imchat.service..domain..",
                        "com.co.kc.imchat.service..infrastructure..",
                        "com.co.kc.imchat.service..interfaces..")
                .check(CLASSES);
    }

    @Test
    void businessServicesMustNotDependOnOtherServiceImplementations() {
        assertServiceIsolation("account", "social", "message");
        assertServiceIsolation("social", "account", "message");
        assertServiceIsolation("message", "account", "social");
    }

    private void assertServiceIsolation(String owner, String... otherServices) {
        String[] forbiddenPackages = java.util.Arrays.stream(otherServices)
                .flatMap(service -> java.util.stream.Stream.of(
                        "com.co.kc.imchat.service." + service + ".application..",
                        "com.co.kc.imchat.service." + service + ".domain..",
                        "com.co.kc.imchat.service." + service + ".infrastructure..",
                        "com.co.kc.imchat.service." + service + ".interfaces.."))
                .toArray(String[]::new);

        noClasses()
                .that().resideInAPackage("com.co.kc.imchat.service." + owner + "..")
                .should().dependOnClassesThat().resideInAnyPackage(forbiddenPackages)
                .check(CLASSES);
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        return current.endsWith("im-architecture-test") ? current.getParent().getParent() : current;
    }
}
