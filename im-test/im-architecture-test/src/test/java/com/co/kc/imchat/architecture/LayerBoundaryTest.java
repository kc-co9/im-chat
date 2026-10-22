package com.co.kc.imchat.architecture;

import com.co.kc.imchat.common.domain.shared.event.DomainEvent;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.lang.ArchRule;

import static org.assertj.core.api.Assertions.assertThat;
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
        brokerSupportPackages("com.co.kc.imchat.broker").check(CLASSES);
    }

    private static ArchRule brokerSupportPackages(String root) {
        return classes().that().resideInAPackage(root + ".support..")
                .should().resideInAnyPackage(root + ".support.exception..", root + ".support.security..",
                        root + ".support.context..", root + ".support.util..",
                        root + ".support.raft.codec", root + ".support.raft.model",
                        root + ".support.raft.statemachine", root + ".support.gossip", root + ".support.monitoring", root + ".support.health")
                .allowEmptyShould(true);
    }

    private static ArchRule brokerRaftSupportDependencies(String root) {
        return noClasses().that().resideInAPackage(root + ".support.raft..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        root + ".application..", root + ".interfaces..", root + ".infrastructure..",
                        root + ".config..", root + ".lifecycle..", root + ".adapter.cluster",
                        root + ".adapter.gateway..", root + ".adapter..client..",
                        "com.co.kc.imchat.plugin.raft.core..", "com.co.kc.imchat.plugin.raft.client..",
                        "com.co.kc.imchat.plugin.bolt..", "com.co.kc.imchat.gateway..",
                        "com.alipay..", "org.springframework..", "io.netty..", "java.net..",
                        "java.net.http..", "okhttp3..", "org.apache.http..")
                .orShould().dependOnClassesThat().haveFullyQualifiedName(
                        "com.co.kc.imchat.broker.sdk.BrokerClient")
                .allowEmptyShould(true);
    }

    private static ArchRule domainCannotDependOnRaftSupport(String root) {
        return noClasses().that().resideInAPackage(root + ".domain..")
                .should().dependOnClassesThat().resideInAPackage(root + ".support.raft..")
                .allowEmptyShould(true);
    }

    @Test
    void brokerRaftSupportCannotOwnRuntimeOrExternalOrchestration() {
        brokerRaftSupportDependencies("com.co.kc.imchat.broker").check(CLASSES);
        domainCannotDependOnRaftSupport("com.co.kc.imchat.broker").check(CLASSES);
    }

    @Test
    void raftSupportRulesAcceptOnlyApprovedPackagesAndRejectOwnershipLeaks() {
        String root = "fixture.broker";
        JavaClasses fixtures = new ClassFileImporter().importPackages(root);
        assertThat(brokerSupportPackages(root).evaluate(fixtures).getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("UnapprovedCapability"))
                .anyMatch(detail -> detail.contains("NestedCodec"))
                .noneMatch(detail -> detail.contains("LegalStateMachine"));
        assertThat(brokerRaftSupportDependencies(root).evaluate(fixtures).getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("RuntimeOwner"))
                .anyMatch(detail -> detail.contains("RpcOwner"))
                .anyMatch(detail -> detail.contains("BrokerClientOwner"))
                .anyMatch(detail -> detail.contains("ApplicationOwner"))
                .anyMatch(detail -> detail.contains("ConfigurationOwner"))
                .anyMatch(detail -> detail.contains("LifecycleOwner"))
                .noneMatch(detail -> detail.contains("LegalStateMachine"))
                .noneMatch(detail -> detail.contains("LegalSdkState"));
        assertThat(domainCannotDependOnRaftSupport(root).evaluate(fixtures).getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("DomainLeak"));
    }

    private static ArchRule brokerGossipSupportDependencies(String root) {
        String[] forbiddenPackages = java.util.stream.Stream.of(root, "com.co.kc.imchat.broker")
                .flatMap(prefix -> java.util.stream.Stream.of(
                        prefix + ".application..", prefix + ".interfaces..", prefix + ".infrastructure..",
                        prefix + ".config..", prefix + ".lifecycle..", prefix + ".adapter.."))
                .toArray(String[]::new);
        return noClasses().that().resideInAPackage(root + ".support.gossip..")
                .should().dependOnClassesThat().resideInAnyPackage(forbiddenPackages)
                .orShould().dependOnClassesThat().resideInAnyPackage(
                        "com.co.kc.imchat.plugin.raft.core..", "com.co.kc.imchat.plugin.raft.client..",
                        "com.co.kc.imchat.plugin.bolt..", "com.co.kc.imchat.gateway..",
                        "com.alipay..", "org.springframework..", "io.netty..", "java.net..",
                        "java.net.http..", "okhttp3..", "org.apache.http..")
                .orShould().dependOnClassesThat().haveFullyQualifiedName(
                        "com.co.kc.imchat.broker.sdk.BrokerClient")
                .allowEmptyShould(true);
    }

    private static ArchRule domainCannotDependOnGossipSupport(String root) {
        return noClasses().that().resideInAPackage(root + ".domain..")
                .should().dependOnClassesThat().resideInAPackage(root + ".support.gossip..")
                .allowEmptyShould(true);
    }

    @Test
    void brokerGossipSupportCannotOwnRuntimeOrExternalOrchestration() {
        brokerGossipSupportDependencies("com.co.kc.imchat.broker").check(CLASSES);
        domainCannotDependOnGossipSupport("com.co.kc.imchat.broker").check(CLASSES);
    }

    @Test
    void gossipSupportRulesAcceptOnlyApprovedPackagesAndRejectOwnershipLeaks() {
        String root = "fixture.broker";
        JavaClasses fixtures = new ClassFileImporter().importPackages(root);
        assertThat(brokerSupportPackages(root).evaluate(fixtures).getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("NestedTracker"))
                .noneMatch(detail -> detail.contains("LegalDiagnosticHistory"));
        assertThat(brokerGossipSupportDependencies(root).evaluate(fixtures).getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("fixture.broker.support.gossip.ApplicationOwner"))
                .anyMatch(detail -> detail.contains("fixture.broker.support.gossip.InterfaceOwner"))
                .anyMatch(detail -> detail.contains("fixture.broker.support.gossip.InfrastructureOwner"))
                .anyMatch(detail -> detail.contains("fixture.broker.support.gossip.ConfigurationOwner"))
                .anyMatch(detail -> detail.contains("fixture.broker.support.gossip.LifecycleOwner"))
                .anyMatch(detail -> detail.contains("fixture.broker.support.gossip.AdapterOwner"))
                .anyMatch(detail -> detail.contains("fixture.broker.support.gossip.RpcOwner"))
                .anyMatch(detail -> detail.contains("fixture.broker.support.gossip.RuntimeOwner"))
                .anyMatch(detail -> detail.contains("fixture.broker.support.gossip.BrokerClientOwner"))
                .anyMatch(detail -> detail.contains("fixture.broker.support.gossip.SpringOwner"))
                .anyMatch(detail -> detail.contains("fixture.broker.support.gossip.NetworkOwner"))
                .noneMatch(detail -> detail.contains("LegalDiagnosticHistory"));
        assertThat(domainCannotDependOnGossipSupport(root).evaluate(fixtures).getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("GossipSupportLeak"));
    }

    private static ArchRule brokerHealthSupportDependencies(String root) {
        String[] forbiddenPackages = java.util.stream.Stream.of(root, "com.co.kc.imchat.broker")
                .flatMap(prefix -> java.util.stream.Stream.of(
                        prefix + ".application..", prefix + ".interfaces..", prefix + ".infrastructure..",
                        prefix + ".config..", prefix + ".configuration..", prefix + ".lifecycle..",
                        prefix + ".adapter..", prefix + ".domain..service..", prefix + ".domain..repository.."))
                .toArray(String[]::new);
        return noClasses().that().resideInAPackage(root + ".support.health..")
                .should().dependOnClassesThat().resideInAnyPackage(forbiddenPackages)
                .orShould().dependOnClassesThat().resideInAnyPackage(
                        "com.co.kc.imchat.plugin..", "com.co.kc.imchat.gateway..", "com.alipay..",
                        "org.springframework..", "io.netty..", "java.net..", "java.net.http..",
                        "okhttp3..", "org.apache.http..", root + "..client..", root + "..network..",
                        "com.co.kc.imchat.broker.sdk..")
                .orShould().dependOnClassesThat(new DescribedPredicate<JavaClass>("own execution resources") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        return javaClass.isAssignableTo(java.util.concurrent.Executor.class)
                                || javaClass.isAssignableTo(Thread.class)
                                || javaClass.isAssignableTo(java.util.Timer.class);
                    }
                }).allowEmptyShould(true);
    }

    private static ArchRule domainCannotDependOnHealthSupport(String root) {
        return noClasses().that().resideInAPackage(root + ".domain..")
                .should().dependOnClassesThat().resideInAPackage(root + ".support.health..")
                .allowEmptyShould(true);
    }

    @Test
    void brokerHealthSupportIsAnExactSafetyInputBoundary() {
        assertThat(CLASSES.stream().anyMatch(type -> type.getPackageName()
                .equals("com.co.kc.imchat.broker.support.health"))).isTrue();
        brokerSupportPackages("com.co.kc.imchat.broker").check(CLASSES);
        brokerHealthSupportDependencies("com.co.kc.imchat.broker").check(CLASSES);
        domainCannotDependOnHealthSupport("com.co.kc.imchat.broker").check(CLASSES);
    }

    @Test
    void healthSupportRulesAcceptSamplesAndRejectOwnershipLeaks() {
        String root = "fixture.broker";
        JavaClasses fixtures = new ClassFileImporter().importPackages(root);
        assertThat(brokerSupportPackages(root).evaluate(fixtures).getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("NestedHealthTracker"))
                .noneMatch(detail -> detail.contains("LegalHealthSamples"));
        java.util.List<String> details = brokerHealthSupportDependencies(root)
                .evaluate(fixtures).getFailureReport().getDetails();
        for (String owner : java.util.List.of("ApplicationOwner", "InterfaceOwner", "InfrastructureOwner",
                "ConfigOwner", "ConfigurationOwner", "LifecycleOwner", "AdapterOwner", "DomainServiceOwner",
                "DomainRepositoryOwner", "SpringOwner", "NetworkOwner", "ClientOwner", "PluginOwner",
                "ExecutorOwner")) {
            assertThat(details).as(owner).anyMatch(detail -> detail.contains(root + ".support.health." + owner));
        }
        assertThat(details).noneMatch(detail -> detail.contains("LegalHealthSamples"));
        assertThat(domainCannotDependOnHealthSupport(root).evaluate(fixtures).getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("HealthSupportLeak"));
    }

    private static ArchRule brokerMonitoringSupportDependencies(String root) {
        String[] forbiddenPackages = java.util.stream.Stream.of(root, "com.co.kc.imchat.broker")
                .flatMap(prefix -> java.util.stream.Stream.of(
                        prefix + ".application..", prefix + ".interfaces..", prefix + ".infrastructure..",
                        prefix + ".config..", prefix + ".lifecycle.."))
                .toArray(String[]::new);
        return noClasses().that().resideInAPackage(root + ".support.monitoring..")
                .should().dependOnClassesThat().resideInAnyPackage(forbiddenPackages)
                .orShould().dependOnClassesThat().resideInAnyPackage(
                        "com.co.kc.imchat.plugin.raft.core..", "com.co.kc.imchat.plugin.raft.client..",
                        "com.co.kc.imchat.plugin.bolt..", "com.co.kc.imchat.gateway..",
                        "com.alipay..", "io.netty..", "java.net..", "java.net.http..",
                        "okhttp3..", "org.apache.http..")
                .orShould().dependOnClassesThat().haveFullyQualifiedName(
                        "com.co.kc.imchat.broker.sdk.BrokerClient")
                .orShould().dependOnClassesThat(new DescribedPredicate<JavaClass>("be an executor or thread owner") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        return javaClass.isAssignableTo(java.util.concurrent.Executor.class)
                                || javaClass.isAssignableTo(Thread.class)
                                || javaClass.isAssignableTo(java.util.Timer.class);
                    }
                })
                .allowEmptyShould(true);
    }

    private static ArchRule domainCannotDependOnMonitoringSupport(String root) {
        return noClasses().that().resideInAPackage(root + ".domain..")
                .should().dependOnClassesThat().resideInAPackage(root + ".support.monitoring..")
                .allowEmptyShould(true);
    }

    private static ArchRule brokerMeterBindersBelongToMonitoringSupport(String root) {
        return classes().that().resideInAPackage(root + "..")
                .and().implement("io.micrometer.core.instrument.binder.MeterBinder")
                .should().resideInAPackage(root + ".support.monitoring")
                .allowEmptyShould(true);
    }

    @Test
    void brokerMonitoringIsAnExactReadonlyTechnicalObserverBoundary() {
        brokerMonitoringSupportDependencies("com.co.kc.imchat.broker").check(CLASSES);
        domainCannotDependOnMonitoringSupport("com.co.kc.imchat.broker").check(CLASSES);
        brokerMeterBindersBelongToMonitoringSupport("com.co.kc.imchat.broker").check(CLASSES);
    }

    @Test
    void monitoringSupportRulesAcceptIntegrationAndRejectOwnershipLeaks() {
        String root = "fixture.broker";
        JavaClasses fixtures = new ClassFileImporter().importPackages(root);
        assertThat(brokerSupportPackages(root).evaluate(fixtures).getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("NestedObserver"))
                .noneMatch(detail -> detail.contains("LegalObserver"));
        java.util.List<String> details = brokerMonitoringSupportDependencies(root)
                .evaluate(fixtures).getFailureReport().getDetails();
        for (String owner : java.util.List.of("ApplicationOwner", "InterfaceOwner", "InfrastructureOwner",
                "ConfigurationOwner", "LifecycleOwner", "RpcOwner", "RuntimeOwner", "BrokerClientOwner",
                "NetworkOwner", "ExecutorOwner")) {
            assertThat(details).as(owner).anyMatch(detail -> detail.contains(root + ".support.monitoring." + owner));
        }
        assertThat(details).noneMatch(detail -> detail.contains("LegalObserver"));
        assertThat(brokerMeterBindersBelongToMonitoringSupport(root).evaluate(fixtures).hasViolation()).isFalse();
        assertThat(domainCannotDependOnMonitoringSupport(root).evaluate(fixtures).getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains("MonitoringSupportLeak"));
    }

    @Test
    void brokerTransformersMustBelongToTheirApprovedCapabilities() {
        classes().that().resideInAPackage("com.co.kc.imchat.broker.transformer..")
                .should().resideInAnyPackage("com.co.kc.imchat.broker.transformer.application",
                        "com.co.kc.imchat.broker.transformer.interfaces",
                        "com.co.kc.imchat.broker.transformer.adapter",
                        "com.co.kc.imchat.broker.transformer.registry")
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
