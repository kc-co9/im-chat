package com.co.kc.imchat.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

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
                        "com.co.kc.imchat.service..")
                .check(CLASSES);
    }

    @Test
    void publicContractsMustNotDependOnServerImplementations() {
        noClasses()
                .that().resideInAnyPackage(
                        "com.co.kc.imchat.broker.sdk..",
                        "com.co.kc.imchat.gateway.ws.sdk..",
                        "com.co.kc.imchat.service.account.facade..",
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
}
