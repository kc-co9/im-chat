package com.co.kc.imchat.architecture;

import com.co.kc.imchat.common.domain.shared.event.DomainEvent;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.base.DescribedPredicate;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

class LayerBoundaryTest {
    @Test
    void brokerEventListenerEntrypointsBelongToInterfaces() {
        classes()
                .that(new DescribedPredicate<JavaClass>("be a Broker Spring event listener entrypoint") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        return javaClass.getPackageName().startsWith("com.co.kc.imchat.broker.")
                                && javaClass.getMethods().stream().anyMatch(method ->
                                        method.isAnnotatedWith("org.springframework.context.event.EventListener")
                                                && method.getRawParameterTypes().stream().anyMatch(parameter ->
                                                        parameter.getPackageName().startsWith(
                                                                "com.co.kc.imchat.broker.model.cqrs.event")));
                    }
                })
                .should().resideInAPackage("com.co.kc.imchat.broker.interfaces..")
                .check(CLASSES);
    }

    @Test
    void brokerScheduledApplicationEntrypointsBelongToInterfaces() {
        classes()
                .that(new DescribedPredicate<JavaClass>("be a scheduled Broker application entrypoint") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        return javaClass.getPackageName().startsWith("com.co.kc.imchat.broker.")
                                && javaClass.getMethods().stream().anyMatch(method ->
                                        method.isAnnotatedWith("org.springframework.scheduling.annotation.Scheduled"))
                                && javaClass.getDirectDependenciesFromSelf().stream().anyMatch(dependency ->
                                        dependency.getTargetClass().getPackageName().startsWith(
                                                "com.co.kc.imchat.broker.application.")
                                                && dependency.getTargetClass().getSimpleName().endsWith("AppService"));
                    }
                })
                .should().resideInAPackage("com.co.kc.imchat.broker.interfaces..")
                .check(CLASSES);
    }

    @Test
    void brokerResultFactoriesMustNotContainMappingNames() {
        noMethods()
                .that().areDeclaredInClassesThat().resideInAPackage("com.co.kc.imchat.broker.model.cqrs.result..")
                .should().haveNameMatching("from.*|.*From")
                .check(CLASSES);
    }

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
                        "com.co.kc.imchat.broker.application..",
                        "com.co.kc.imchat.management..interfaces..",
                        "com.co.kc.imchat.management..infrastructure..",
                        "com.co.kc.imchat.management..config..",
                        "com.co.kc.imchat.management..lifecycle..",
                        "com.co.kc.imchat.service..interfaces..",
                        "com.co.kc.imchat.service..infrastructure..")
                .check(CLASSES);
    }

    @Test
    void brokerDomainMustNotDependOnTechnicalLayers() {
        noClasses()
                .that().resideInAPackage("com.co.kc.imchat.broker.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.co.kc.imchat.broker.interfaces..",
                        "com.co.kc.imchat.broker.support..",
                        "com.co.kc.imchat.broker.infrastructure..",
                        "com.co.kc.imchat.broker.sdk..",
                        "com.co.kc.imchat.plugin..",
                        "org.springframework..")
                .check(CLASSES);
    }

    @Test
    void brokerDomainPackagesMustMatchCapabilityRoles() {
        classes()
                .that().resideInAPackage("com.co.kc.imchat.broker.domain..")
                .should().resideInAnyPackage(
                        "com.co.kc.imchat.broker.domain.cluster.model",
                        "com.co.kc.imchat.broker.domain.cluster.repository",
                        "com.co.kc.imchat.broker.domain.cluster.service",
                        "com.co.kc.imchat.broker.domain.authority.model",
                        "com.co.kc.imchat.broker.domain.authority.service",
                        "com.co.kc.imchat.broker.domain.authority.repository",
                        "com.co.kc.imchat.broker.domain.connection.model",
                        "com.co.kc.imchat.broker.domain.connection.repository")
                .check(CLASSES);

    }

    @Test
    void brokerDomainMustNotContainConcreteAdapters() {
        noClasses()
                .that().resideInAPackage("com.co.kc.imchat.broker.domain..")
                .should().haveSimpleNameStartingWith("InMemory")
                .check(CLASSES);
    }

    @Test
    void brokerDomainRepositoriesMustBeInterfaces() {
        classes()
                .that().resideInAnyPackage(
                        "com.co.kc.imchat.broker.domain.cluster.repository",
                        "com.co.kc.imchat.broker.domain.authority.repository",
                        "com.co.kc.imchat.broker.domain.connection.repository")
                .should().beInterfaces()
                .check(CLASSES);
    }

    @Test
    void brokerSupportMustOnlyContainNarrowCrossLayerHelpers() {
        classes()
                .that().resideInAPackage("com.co.kc.imchat.broker.support..")
                .should().resideInAnyPackage(
                        "com.co.kc.imchat.broker.support.exception..",
                        "com.co.kc.imchat.broker.support.security..",
                        "com.co.kc.imchat.broker.support.context..",
                        "com.co.kc.imchat.broker.support.util..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void brokerRequestContextMustNotDependOnBusinessImplementations() {
        noClasses()
                .that().resideInAPackage("com.co.kc.imchat.broker.support.context..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.co.kc.imchat.broker.application..",
                        "com.co.kc.imchat.broker.domain..",
                        "com.co.kc.imchat.broker.adapter..",
                        "com.co.kc.imchat.broker.interfaces..",
                        "com.co.kc.imchat.broker.infrastructure.registry..")
                .check(CLASSES);
    }

    @Test
    void brokerApplicationMustNotDependOnConcreteOuterLayers() {
        noClasses()
                .that().resideInAPackage("com.co.kc.imchat.broker.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.co.kc.imchat.broker.interfaces..",
                        "com.co.kc.imchat.broker.infrastructure..",
                        "com.co.kc.imchat.broker.support.client..",
                        "com.co.kc.imchat.gateway.ws.sdk..",
                        "com.co.kc.imchat.plugin.bolt..")
                .check(CLASSES);
    }

    @Test
    void brokerMustNotReintroduceGenericConfigOrLifecyclePackages() {
        noClasses()
                .should().resideInAnyPackage(
                        "com.co.kc.imchat.broker.config..",
                        "com.co.kc.imchat.broker.lifecycle..")
                .check(CLASSES);
    }

    @Test
    void brokerApplicationMustGroupUseCasesByCapability() {
        noClasses()
                .should().resideInAnyPackage(
                        "com.co.kc.imchat.broker.application..model..",
                        "com.co.kc.imchat.broker.application.service..",
                        "com.co.kc.imchat.broker.application.port..",
                        "com.co.kc.imchat.broker.application.event..")
                .check(CLASSES);

        classes()
                .that().resideInAPackage("com.co.kc.imchat.broker.application..")
                .should().resideInAnyPackage(
                        "com.co.kc.imchat.broker.application.cluster..",
                        "com.co.kc.imchat.broker.application.frame..",
                        "com.co.kc.imchat.broker.application.local..",
                        "com.co.kc.imchat.broker.application.gossip..",
                        "com.co.kc.imchat.broker.application.diagnostic..",
                        "com.co.kc.imchat.broker.application.policy..")
                .check(CLASSES);
    }

    @Test
    void brokerModelsMustBeGroupedByTypeResponsibility() {
        classes()
                .that().resideInAPackage("com.co.kc.imchat.broker.model..")
                .should().resideInAnyPackage(
                        "com.co.kc.imchat.broker.model.cqrs.command..",
                        "com.co.kc.imchat.broker.model.cqrs.dto..",
                        "com.co.kc.imchat.broker.model.cqrs.event..",
                        "com.co.kc.imchat.broker.model.cqrs.result..",
                        "com.co.kc.imchat.broker.model.cqrs.query..",
                        "com.co.kc.imchat.broker.model.properties..",
                        "com.co.kc.imchat.broker.model.enums..",
                        "com.co.kc.imchat.broker.model.io..")
                .check(CLASSES);
    }

    @Test
    void brokerEventsMustUseTheSharedDomainEventContract() {
        classes()
                .that().resideInAPackage("com.co.kc.imchat.broker.model.cqrs.event..")
                .should().implement(DomainEvent.class)
                .check(CLASSES);
    }

    @Test
    void brokerInterfacesMustNotDependOnConcreteInfrastructure() {
        noClasses()
                .that().resideInAPackage("com.co.kc.imchat.broker.interfaces..")
                .should().dependOnClassesThat().resideInAPackage(
                        "com.co.kc.imchat.broker.infrastructure..")
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
