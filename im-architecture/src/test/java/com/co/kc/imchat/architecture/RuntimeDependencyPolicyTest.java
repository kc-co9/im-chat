package com.co.kc.imchat.architecture;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeDependencyPolicyTest {
    private static final Path REPO_ROOT = Path.of("").toAbsolutePath().getParent();
    private static final Pattern PUBLIC_RECORD = Pattern.compile("\\bpublic\\s+record\\s+\\w+\\s*\\(");
    private static final Pattern SNOWFLAKE_INFRASTRUCTURE_CONSTRUCTION = Pattern.compile(
            "\\bnew\\s+(?:[\\w.]+\\.)?(?:SnowflakeId|StaticSnowflakeMachineId|RedisSnowflakeMachineId)\\s*\\(");

    @Test
    void managementModulesIncludeIamAndIndependentApplications() throws Exception {
        Path rootPom = REPO_ROOT.resolve("pom.xml");
        Path managementPom = REPO_ROOT.resolve("im-management/pom.xml");
        Path adminPom = REPO_ROOT.resolve("im-management/im-admin/pom.xml");
        Path monitorPom = REPO_ROOT.resolve("im-management/im-monitor/pom.xml");
        Path iamPom = REPO_ROOT.resolve("im-management/im-iam/pom.xml");
        Path iamServerPom = REPO_ROOT.resolve(
                "im-management/im-iam/im-iam-server/pom.xml");
        Path auditPom = REPO_ROOT.resolve("im-management/im-audit/pom.xml");
        Path auditSdkPom = REPO_ROOT.resolve(
                "im-management/im-audit/im-audit-sdk/pom.xml");
        Path auditServerPom = REPO_ROOT.resolve(
                "im-management/im-audit/im-audit-server/pom.xml");

        assertThat(moduleNames(rootPom)).contains("im-management");
        assertThat(managementPom).exists();
        assertThat(adminPom).exists();
        assertThat(monitorPom).exists();
        assertThat(iamPom).exists();
        assertThat(auditPom).exists();
        assertThat(moduleNames(managementPom))
                .containsExactlyInAnyOrder("im-iam", "im-admin", "im-monitor", "im-audit");
        assertThat(moduleNames(iamPom))
                .containsExactlyInAnyOrder("im-iam-server", "im-iam-sdk");
        assertThat(moduleNames(auditPom))
                .containsExactlyInAnyOrder("im-audit-server", "im-audit-sdk");
        assertThat(auditSdkPom).exists();
        assertThat(auditServerPom).exists();
        assertThat(hasManagedDependency(rootPom, "com.co.kc.im", "im-audit-sdk"))
                .isTrue();
        assertThat(hasDependency(auditServerPom, "com.co.kc.im", "im-audit-sdk"))
                .isTrue();
        assertThat(hasDependency(auditServerPom, "com.co.kc.im", "im-iam-sdk"))
                .isTrue();
        assertThat(isOptionalDependency(auditSdkPom, "com.co.kc.im", "im-iam-sdk"))
                .isTrue();
        assertThat(hasDependency(auditServerPom, "com.co.kc.im", "im-dubbo"))
                .isFalse();
        assertThat(hasDependency(auditSdkPom, "com.co.kc.im", "im-dubbo"))
                .isFalse();
        assertThat(hasDependency(iamServerPom, "com.co.kc.im", "im-iam-sdk"))
                .isFalse();
        assertThat(hasDependency(iamServerPom, "com.co.kc.im", "im-dubbo"))
                .isFalse();
        assertThat(hasDependency(auditSdkPom, "com.co.kc.im", "im-audit-server"))
                .isFalse();
        assertThat(hasDependency(monitorPom, "com.co.kc.im", "im-broker-server"))
                .isFalse();
        assertThat(hasDependency(adminPom, "com.co.kc.im", "im-iam-sdk")).isTrue();
        assertThat(hasDependency(monitorPom, "com.co.kc.im", "im-iam-sdk")).isTrue();
        assertThat(List.of(adminPom, auditServerPom, iamServerPom, monitorPom))
                .allMatch(pom -> hasDependency(pom, "com.co.kc.im", "im-nacos"));
    }

    @Test
    void kafkaMqPluginOwnsOnlyKafkaRuntimeIntegration() throws Exception {
        Path rootPom = REPO_ROOT.resolve("pom.xml");
        Path pluginPom = REPO_ROOT.resolve("im-plugin/pom.xml");
        Path kafkaPom = REPO_ROOT.resolve("im-plugin/im-mq-kafka/pom.xml");
        Path legacySources = REPO_ROOT.resolve(
                "im-plugin/im-mq-kafka/src/main/java/com/co/kc/imchat/plugin/mq");

        assertThat(moduleNames(pluginPom)).contains("im-mq-kafka");
        assertThat(hasManagedDependency(rootPom, "com.co.kc.im", "im-mq-kafka"))
                .isTrue();
        assertThat(kafkaPom).exists();
        assertThat(hasDependency(
                kafkaPom,
                "org.springframework.cloud",
                "spring-cloud-stream-binder-kafka"))
                .isTrue();
        assertThat(legacySources.resolve("spi/MessagePublisher.java")).doesNotExist();
        assertThat(legacySources.resolve("spi/MessageSubscriber.java")).doesNotExist();
        assertThat(legacySources.resolve("model/MqMessage.java")).doesNotExist();
        assertThat(legacySources.resolve("core/InMemoryMessageBus.java")).doesNotExist();
    }

    @Test
    void identityPluginOwnsSnowflakeWithoutTransitiveRedis() throws Exception {
        Path identityPom = REPO_ROOT.resolve("im-plugin/im-identity/pom.xml");
        Path commonIdentity = REPO_ROOT.resolve(
                "im-common/src/main/java/com/co/kc/imchat/common/identity");
        List<Path> consumers = List.of(
                REPO_ROOT.resolve("im-service/im-account/im-account-server/pom.xml"),
                REPO_ROOT.resolve("im-service/im-social/im-social-server/pom.xml"),
                REPO_ROOT.resolve("im-service/im-message/im-message-server/pom.xml"),
                REPO_ROOT.resolve("im-management/im-iam/im-iam-server/pom.xml"));

        assertThat(commonIdentity).doesNotExist();
        assertThat(isOptionalDependency(
                identityPom,
                "org.redisson",
                "redisson-spring-boot-starter"))
                .isTrue();
        assertThat(consumers)
                .allMatch(pom -> hasDependency(pom, "com.co.kc.im", "im-identity"));
    }

    @Test
    void runtimeModulesDoNotConstructSnowflakeInfrastructure() throws IOException {
        Path identitySources = REPO_ROOT.resolve("im-plugin/im-identity/src/main/java");
        List<Path> productionSources;
        try (Stream<Path> paths = Files.walk(REPO_ROOT)) {
            productionSources = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> !path.startsWith(identitySources))
                    .toList();
        }

        List<Path> violations = new ArrayList<>();
        for (Path source : productionSources) {
            if (constructsSnowflakeInfrastructure(Files.readString(source))) {
                violations.add(source);
            }
        }

        assertThat(violations)
                .as("Snowflake implementations must be selected by im-identity configuration")
                .isEmpty();
    }

    @Test
    void snowflakeConstructionScanRecognizesOwnedAndUnrelatedCode() {
        assertThat(constructsSnowflakeInfrastructure(
                "return new SnowflakeId(new StaticSnowflakeMachineId(1, 1));"))
                .isTrue();
        assertThat(constructsSnowflakeInfrastructure(
                "private final SnowflakeId snowflakeId;"))
                .isFalse();
    }

    @Test
    void managementAuditPersistenceIsCentralizedInAuditServer() throws Exception {
        Path adminPom = REPO_ROOT.resolve("im-management/im-admin/pom.xml");
        Path iamServerPom = REPO_ROOT.resolve(
                "im-management/im-iam/im-iam-server/pom.xml");
        Path auditServerRoot = REPO_ROOT.resolve(
                "im-management/im-audit/im-audit-server");

        assertThat(hasDependency(adminPom, "com.co.kc.im", "im-audit-sdk")).isTrue();
        assertThat(hasDependency(iamServerPom, "com.co.kc.im", "im-audit-sdk")).isTrue();
        assertThat(hasDependency(adminPom, "com.co.kc.im", "im-audit-server")).isFalse();
        assertThat(hasDependency(iamServerPom, "com.co.kc.im", "im-audit-server")).isFalse();

        List<Path> localPersistence;
        try (Stream<Path> sources = Files.walk(REPO_ROOT.resolve("im-management"))) {
            localPersistence = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> !path.startsWith(auditServerRoot))
                    .filter(RuntimeDependencyPolicyTest::isLocalAuditPersistence)
                    .toList();
        }

        assertThat(localPersistence)
                .as("management applications must publish through im-audit-sdk instead of owning audit persistence")
                .isEmpty();
    }

    @Test
    void managementAuditPersistenceScanIgnoresProducerSupportAndDomainModels() {
        assertThat(isLocalAuditPersistence(Path.of(
                "/repo/im-management/app/src/main/java/example/domain/repository/AuditRepository.java")))
                .isTrue();
        assertThat(isLocalAuditPersistence(Path.of(
                "/repo/im-management/app/src/main/java/example/domain/audit/repository/AuditRepository.java")))
                .isTrue();
        assertThat(isLocalAuditPersistence(Path.of(
                "/repo/im-management/app/src/main/java/example/infrastructure/mybatis/entity/DbSecurityAudit.java")))
                .isTrue();
        assertThat(isLocalAuditPersistence(Path.of(
                "/repo/im-management/app/src/main/java/example/support/audit/AuditPublisher.java")))
                .isFalse();
        assertThat(isLocalAuditPersistence(Path.of(
                "/repo/im-management/app/src/main/java/example/domain/audit/model/AuditAction.java")))
                .isFalse();
        assertThat(isLocalAuditPersistence(Path.of(
                "/repo/im-management/app/src/main/java/example/domain/model/AuditRepository.java")))
                .isFalse();
    }

    @Test
    void managementApplicationsDoNotOwnAdministratorAuthenticationState() throws Exception {
        List<Path> applicationSources = List.of(
                REPO_ROOT.resolve("im-management/im-admin/src/main/java"),
                REPO_ROOT.resolve("im-management/im-monitor/src/main/java"));
        List<String> forbiddenOwnershipPaths = List.of(
                "/domain/administrator/",
                "/infrastructure/session/",
                "/infrastructure/authentication/");

        for (Path sourceRoot : applicationSources) {
            List<Path> violations;
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                violations = paths.filter(Files::isRegularFile)
                        .filter(path -> {
                            String normalizedPath = path.toString().replace('\\', '/');
                            return forbiddenOwnershipPaths.stream()
                                    .anyMatch(normalizedPath::contains);
                        })
                        .toList();
            }
            assertThat(violations).isEmpty();
        }
    }

    @Test
    void accountAdminFacadeHasDedicatedRuntimeBoundary() throws Exception {
        Path accountPom = REPO_ROOT.resolve("im-service/im-account/pom.xml");
        Path facadePom = REPO_ROOT.resolve(
                "im-service/im-account/im-account-admin-facade/pom.xml");
        Path accountServerPom = REPO_ROOT.resolve(
                "im-service/im-account/im-account-server/pom.xml");
        Path adminPom = REPO_ROOT.resolve("im-management/im-admin/pom.xml");
        Path monitorPom = REPO_ROOT.resolve("im-management/im-monitor/pom.xml");

        assertThat(moduleNames(accountPom)).contains("im-account-admin-facade");
        assertThat(facadePom).exists();
        assertThat(hasDependency(accountServerPom, "com.co.kc.im", "im-account-admin-facade"))
                .isTrue();
        assertThat(hasDependency(adminPom, "com.co.kc.im", "im-account-admin-facade"))
                .isTrue();
        assertThat(hasDependency(adminPom, "com.co.kc.im", "im-account-server"))
                .isFalse();
        assertThat(hasDependency(adminPom, "com.co.kc.im", "im-datasource"))
                .isFalse();
        assertThat(hasDependency(monitorPom, "com.co.kc.im", "im-account-admin-facade"))
                .isFalse();
        assertThat(packaging(adminPom)).isNotEqualTo("pom");
    }

    @Test
    void productionModulesDoNotIncludeH2OutsideTestScope() throws Exception {
        List<Path> poms;
        try (var paths = Files.walk(REPO_ROOT)) {
            poms = paths
                    .filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .toList();
        }

        assertThat(poms)
                .filteredOn(RuntimeDependencyPolicyTest::hasRuntimeH2Dependency)
                .isEmpty();
    }

    @Test
    void servletWebServicesDependOnImWebPlugin() throws Exception {
        List<Path> servicePoms;
        try (var paths = Files.walk(REPO_ROOT.resolve("im-service"))) {
            servicePoms = paths
                    .filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .filter(path -> path.toString().endsWith("-server/pom.xml"))
                    .filter(path -> hasDependency(path, "org.springframework.boot", "spring-boot-starter-web"))
                    .toList();
        }

        assertThat(servicePoms)
                .filteredOn(path -> !hasDependency(path, "com.co.kc.im", "im-web"))
                .isEmpty();
    }

    @Test
    void webInfrastructureDependenciesFollowOwnership() throws Exception {
        Path webPom = REPO_ROOT.resolve("im-plugin/im-web/pom.xml");
        Path adminPom = REPO_ROOT.resolve("im-management/im-admin/pom.xml");
        Path monitorPom = REPO_ROOT.resolve("im-management/im-monitor/pom.xml");
        Path iamSdkPom = REPO_ROOT.resolve("im-management/im-iam/im-iam-sdk/pom.xml");

        assertThat(hasDependency(webPom, "com.co.kc.im", "im-session")).isFalse();
        assertThat(hasDependency(adminPom, "com.co.kc.im", "im-web")).isTrue();
        assertThat(hasDependency(monitorPom, "com.co.kc.im", "im-web")).isTrue();
        assertThat(hasDependency(iamSdkPom, "com.co.kc.im", "im-web")).isTrue();
    }

    @Test
    void deployableServletApplicationsConfigureOpenApiPaths() throws IOException {
        List<Path> applicationConfigs = List.of(
                REPO_ROOT.resolve("im-broker/im-broker-server/src/main/resources/application.yml"),
                REPO_ROOT.resolve("im-management/im-admin/src/main/resources/application.yml"),
                REPO_ROOT.resolve("im-management/im-audit/im-audit-server/src/main/resources/application.yml"),
                REPO_ROOT.resolve("im-management/im-iam/im-iam-server/src/main/resources/application.yml"),
                REPO_ROOT.resolve("im-management/im-monitor/src/main/resources/application.yml"),
                REPO_ROOT.resolve("im-service/im-account/im-account-server/src/main/resources/application.yml"),
                REPO_ROOT.resolve("im-service/im-message/im-message-server/src/main/resources/application.yml"),
                REPO_ROOT.resolve("im-service/im-social/im-social-server/src/main/resources/application.yml"));

        for (Path applicationConfig : applicationConfigs) {
            assertThat(Files.readString(applicationConfig))
                    .as(applicationConfig.toString())
                    .contains(
                            "springdoc:",
                            "path: /api/doc.html",
                            "path: /v3/api-docs");
        }
    }

    @Test
    void managementApplicationsUseApprovedLocalPortMap() throws IOException {
        assertManagementPort(
                "im-management/im-iam/im-iam-server/src/main/resources/application.yml",
                18090);
        assertManagementPort(
                "im-management/im-audit/im-audit-server/src/main/resources/application.yml",
                18091);
        assertManagementPort(
                "im-management/im-monitor/src/main/resources/application.yml",
                18092);
        assertManagementPort(
                "im-management/im-admin/src/main/resources/application.yml",
                18093);
    }

    private void assertManagementPort(String relativePath, int port) throws IOException {
        String configuration = Files.readString(REPO_ROOT.resolve(relativePath));
        assertThat(configuration)
                .as(relativePath)
                .contains("port: " + port);
    }

    @Test
    void rpcContractRecordsImplementSerializable() throws Exception {
        List<Path> rpcContractSources;
        try (var paths = Files.walk(REPO_ROOT)) {
            rpcContractSources = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(RuntimeDependencyPolicyTest::isRpcContractSource)
                    .filter(RuntimeDependencyPolicyTest::isPublicRecordWithoutSerializable)
                    .toList();
        }

        assertThat(rpcContractSources).isEmpty();
    }

    private static boolean isRpcContractSource(Path path) {
        String sourcePath = path.toString();
        return sourcePath.contains("-facade/src/main/java/")
                || sourcePath.contains("/im-broker-sdk/src/main/java/")
                || sourcePath.contains("/im-ws-gateway-sdk/src/main/java/");
    }

    private static boolean isLocalAuditPersistence(Path path) {
        String normalized = path.toString().replace('\\', '/');
        String typeName = path.getFileName().toString();
        return (normalized.contains("/domain/")
                && normalized.contains("/repository/")
                && typeName.contains("Audit"))
                || (normalized.contains("/infrastructure/domain/repository/")
                && typeName.contains("Audit"))
                || (normalized.contains("/infrastructure/mybatis/")
                && typeName.contains("Audit"));
    }

    private static boolean isPublicRecordWithoutSerializable(Path path) {
        try {
            String source = Files.readString(path);
            return PUBLIC_RECORD.matcher(source).find()
                    && !source.contains("implements Serializable");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to inspect source: " + path, ex);
        }
    }

    private static boolean hasRuntimeH2Dependency(Path pom) {
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(pom.toFile());
            NodeList dependencies = document.getElementsByTagName("dependency");
            for (int i = 0; i < dependencies.getLength(); i++) {
                Element dependency = (Element) dependencies.item(i);
                if (matchesDependency(dependency, "com.h2database", "h2")
                        && !"test".equals(childText(dependency, "scope"))) {
                    return true;
                }
            }
            return false;
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            throw new IllegalStateException("Failed to inspect pom: " + pom, ex);
        }
    }

    private static boolean hasDependency(Path pom, String groupId, String artifactId) {
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(pom.toFile());
            NodeList dependencies = document.getElementsByTagName("dependency");
            for (int i = 0; i < dependencies.getLength(); i++) {
                if (matchesDependency((Element) dependencies.item(i), groupId, artifactId)) {
                    return true;
                }
            }
            return false;
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            throw new IllegalStateException("Failed to inspect pom: " + pom, ex);
        }
    }

    private static boolean isOptionalDependency(
            Path pom,
            String groupId,
            String artifactId
    ) {
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(pom.toFile());
            NodeList dependencies = document.getElementsByTagName("dependency");
            for (int i = 0; i < dependencies.getLength(); i++) {
                Element dependency = (Element) dependencies.item(i);
                if (matchesDependency(dependency, groupId, artifactId)) {
                    return "true".equals(childText(dependency, "optional"));
                }
            }
            return false;
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            throw new IllegalStateException("Failed to inspect pom: " + pom, ex);
        }
    }

    private static boolean constructsSnowflakeInfrastructure(String source) {
        return SNOWFLAKE_INFRASTRUCTURE_CONSTRUCTION.matcher(source).find();
    }

    private static boolean hasManagedDependency(
            Path pom,
            String groupId,
            String artifactId
    ) {
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(pom.toFile());
            NodeList managementNodes = document.getElementsByTagName("dependencyManagement");
            if (managementNodes.getLength() == 0) {
                return false;
            }
            Element management = (Element) managementNodes.item(0);
            NodeList dependencies = management.getElementsByTagName("dependency");
            for (int i = 0; i < dependencies.getLength(); i++) {
                if (matchesDependency((Element) dependencies.item(i), groupId, artifactId)) {
                    return true;
                }
            }
            return false;
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            throw new IllegalStateException("Failed to inspect pom: " + pom, ex);
        }
    }

    private static List<String> moduleNames(Path pom) {
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(pom.toFile());
            NodeList modules = document.getElementsByTagName("module");
            return java.util.stream.IntStream.range(0, modules.getLength())
                    .mapToObj(index -> modules.item(index).getTextContent().trim())
                    .toList();
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            throw new IllegalStateException("Failed to inspect pom: " + pom, ex);
        }
    }

    private static String packaging(Path pom) {
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(pom.toFile());
            NodeList nodes = document.getDocumentElement().getElementsByTagName("packaging");
            return nodes.getLength() == 0 ? "jar" : nodes.item(0).getTextContent().trim();
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            throw new IllegalStateException("Failed to inspect pom: " + pom, ex);
        }
    }

    private static boolean matchesDependency(Element dependency, String groupId, String artifactId) {
        return groupId.equals(childText(dependency, "groupId"))
                && artifactId.equals(childText(dependency, "artifactId"));
    }

    private static String childText(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        Node node = nodes.item(0);
        return node.getTextContent().trim();
    }
}
