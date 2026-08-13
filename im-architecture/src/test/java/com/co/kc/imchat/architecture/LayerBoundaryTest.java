package com.co.kc.imchat.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class LayerBoundaryTest {
    private static final String[] DOMAIN_PACKAGES = {
            "com.co.kc.imchat.broker.domain..",
            "com.co.kc.imchat.service.account.domain..",
            "com.co.kc.imchat.service.social.domain..",
            "com.co.kc.imchat.service.message.domain.."
    };
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .importPackages("com.co.kc.imchat.broker", "com.co.kc.imchat.service");

    @Test
    void domainsMustNotDependOnOuterApplicationLayers() {
        noClasses()
                .that().resideInAnyPackage(DOMAIN_PACKAGES)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.co.kc.imchat.broker.interfaces..",
                        "com.co.kc.imchat.broker.lifecycle..",
                        "com.co.kc.imchat.service..interfaces..",
                        "com.co.kc.imchat.service..infrastructure..")
                .check(CLASSES);
    }

    @Test
    void domainsMustNotDependOnPersistenceFrameworks() {
        noClasses()
                .that().resideInAnyPackage(DOMAIN_PACKAGES)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.baomidou..",
                        "org.apache.ibatis..",
                        "jakarta.persistence..")
                .check(CLASSES);
    }

    @Test
    void interfacesMustNotDependOnConcretePersistenceTypes() {
        noClasses()
                .that().resideInAPackage("..interfaces..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure.mybatis..",
                        "..infrastructure.domain.repository..")
                .check(CLASSES);
    }
}
