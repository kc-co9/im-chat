package com.co.kc.imchat.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.base.DescribedPredicate;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class LayerBoundaryTest {
    private static final DescribedPredicate<JavaClass> TOKEN_CRYPTOGRAPHY_TYPES =
            new DescribedPredicate<>("be a concrete Token cryptography type") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return javaClass.getPackageName().startsWith("javax.crypto")
                            || java.util.Set.of(
                                    "com.co.kc.imchat.plugin.session.token.codec.JwtTokenCodec",
                                    "com.co.kc.imchat.plugin.session.token.model.DecodedToken",
                                    "com.co.kc.imchat.plugin.session.token.model.TokenClaims",
                                    "com.co.kc.imchat.plugin.session.token.model.TokenType")
                            .contains(javaClass.getName());
                }
            };
    private static final String[] DOMAIN_PACKAGES = {
            "com.co.kc.imchat.broker.domain..",
            "com.co.kc.imchat.management.admin.domain..",
            "com.co.kc.imchat.management.audit.domain..",
            "com.co.kc.imchat.management.iam.domain..",
            "com.co.kc.imchat.service.account.domain..",
            "com.co.kc.imchat.service.social.domain..",
            "com.co.kc.imchat.service.message.domain.."
    };
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .importPackages(
                    "com.co.kc.imchat.broker",
                    "com.co.kc.imchat.management",
                    "com.co.kc.imchat.service");

    @Test
    void domainsMustNotDependOnOuterApplicationLayers() {
        noClasses()
                .that().resideInAnyPackage(DOMAIN_PACKAGES)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.co.kc.imchat.broker.interfaces..",
                        "com.co.kc.imchat.broker.lifecycle..",
                        "com.co.kc.imchat.management..interfaces..",
                        "com.co.kc.imchat.management..infrastructure..",
                        "com.co.kc.imchat.management..config..",
                        "com.co.kc.imchat.management..lifecycle..",
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

    @Test
    void managementApplicationsMustNotDependOnOuterLayers() {
        noClasses()
                .that().resideInAPackage("com.co.kc.imchat.management..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.co.kc.imchat.management..interfaces..",
                        "com.co.kc.imchat.management..infrastructure..",
                        "com.co.kc.imchat.management..config..",
                        "com.co.kc.imchat.management..lifecycle..")
                .check(CLASSES);
    }

    @Test
    void applicationServicesMustNotDependOnOtherApplicationServices() {
        noClasses()
                .that().haveSimpleNameEndingWith("AppService")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("AppService")
                .check(CLASSES);
    }

    @Test
    void accountApplicationMustNotImplementTokenCryptography() {
        noClasses()
                .that().resideInAPackage("com.co.kc.imchat.service.account.application..")
                .should().dependOnClassesThat(TOKEN_CRYPTOGRAPHY_TYPES)
                .check(CLASSES);
    }
}
