package com.co.kc.imchat.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityConfigurationTest {

    private static final Path REPO_ROOT = findRepositoryRoot();
    private static final List<ApplicationPorts> APPLICATIONS = List.of(
            application("im-gateway/im-http-gateway", "server.port", 18010, 19010),
            application("im-gateway/im-ws-gateway/im-ws-gateway-server", "im.gateway.ws.port", 18011, 19011),
            application("im-broker/im-broker-server", "im.bolt.server.port", 18020, 19020),
            application("im-service/im-account/im-account-server", "server.port", 18030, 19030),
            application("im-service/im-social/im-social-server", "server.port", 18031, 19031),
            application("im-service/im-message/im-message-server", "server.port", 18032, 19032),
            application("im-management/im-iam/im-iam-server", "server.port", 18040, 19040),
            application("im-management/im-audit/im-audit-server", "server.port", 18041, 19041),
            application("im-management/im-admin", "server.port", 18042, 19042),
            application("im-management/im-monitor", "server.port", 18043, 19043));
    private static final List<String> SERVLET_SECURITY_OWNERS = List.of(
            "im-management/im-iam/im-iam-sdk/src/main/java/com/co/kc/imchat/management/iam/sdk/ImIamSdkAutoConfiguration.java",
            "im-management/im-admin/src/main/java/com/co/kc/imchat/management/admin/infrastructure/config/beans/AdminSecurityBeans.java",
            "im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/infrastructure/config/beans/AuditSecurityBeans.java",
            "im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/config/beans/IamSecurityBeans.java");

    @Test
    void applicationsUseGovernedBusinessAndManagementPorts() throws Exception {
        Set<Integer> ports = new HashSet<>();
        for (ApplicationPorts application : APPLICATIONS) {
            List<PropertySource<?>> sources = load(application.applicationPath());

            assertThat(property(sources, application.businessPortProperty()))
                    .as(application.modulePath())
                    .isEqualTo(application.businessPort());
            assertThat(property(sources, "management.server.port"))
                    .as(application.modulePath())
                    .isEqualTo(application.managementPort());
            assertThat(application.managementPort() - application.businessPort()).isEqualTo(1000);
            assertThat(ports.add(application.businessPort())).as(application.modulePath()).isTrue();
            assertThat(ports.add(application.managementPort())).as(application.modulePath()).isTrue();
        }
    }

    @Test
    void applicationsExposeTheApprovedActuatorEndpoints() throws Exception {
        for (ApplicationPorts application : APPLICATIONS) {
            List<PropertySource<?>> sources = load(application.applicationPath());

            assertThat(property(sources, "management.endpoints.web.exposure.include"))
                    .as(application.modulePath())
                    .isEqualTo("health,info,prometheus,metrics");
            assertThat(property(sources, "management.endpoint.health.show-details"))
                    .as(application.modulePath())
                    .isEqualTo("never");
        }
    }

    @Test
    void nacosPublishesTheManagementPortForEveryApplication() throws Exception {
        List<PropertySource<?>> sources = load(
                "im-plugin/im-nacos/src/main/resources/META-INF/config/im-nacos.yml");

        assertThat(property(sources, "spring.cloud.nacos.discovery.metadata.management-port"))
                .isEqualTo("${management.server.port}");
    }

    @Test
    void applicationsDependDirectlyOnTheMetricsPlugin() throws Exception {
        for (ApplicationPorts application : APPLICATIONS) {
            Set<String> dependencies = directDependencyArtifactIds(application.pomPath());

            assertThat(dependencies).as(application.modulePath()).contains("im-metrics");
        }
        assertThat(directDependencyArtifactIds("im-plugin/im-metrics/pom.xml"))
                .contains("spring-boot-starter-actuator", "micrometer-registry-prometheus");
    }

    @Test
    void iamSeedUsesGovernedManagementOAuthCallbacks() throws Exception {
        String dml = Files.readString(REPO_ROOT.resolve(
                "im-management/im-iam/im-iam-server/sql/dml.sql"));

        assertThat(dml)
                .contains("'im-admin-client'", "http://localhost:18042/iam/callback")
                .contains("'im-audit-client'", "http://localhost:18041/iam/callback")
                .contains("'im-monitor-client'", "http://localhost:18043/iam/callback");
    }

    @Test
    void securityOwnersPermitActuatorWithoutMetricsOwningTheBusinessSecurityChain() throws Exception {
        for (String sourcePath : SERVLET_SECURITY_OWNERS) {
            assertThat(Files.readString(REPO_ROOT.resolve(sourcePath)))
                    .as(sourcePath)
                    .containsPattern("\\.requestMatchers\\(\\s*\"/actuator/\\*\\*\"\\s*\\)"
                            + "\\s*\\.permitAll\\(\\)");
        }
        String autoConfigurationImports = Files.readString(REPO_ROOT.resolve(
                "im-plugin/im-metrics/src/main/resources/META-INF/spring/"
                        + "org.springframework.boot.autoconfigure.AutoConfiguration.imports"));
        assertThat(autoConfigurationImports).doesNotContain("SecurityAutoConfiguration");
    }

    @Test
    void applicationsDependOnTracingAndIncludeTheCommonLogbackConfiguration() throws Exception {
        String commonResource = "com/co/kc/imchat/plugin/tracing/logback-common.xml";
        for (ApplicationPorts application : APPLICATIONS) {
            assertThat(directDependencyArtifactIds(application.pomPath()))
                    .as(application.modulePath())
                    .contains("im-tracing");
            Path logbackPath = REPO_ROOT.resolve(
                    application.modulePath() + "/src/main/resources/logback-spring.xml");
            assertThat(logbackPath).as(application.modulePath()).isRegularFile();
            assertThat(Files.readString(logbackPath))
                    .contains("<include resource=\"" + commonResource + "\"/>")
                    .contains("<appender-ref ref=\"IM_CONSOLE\"/>");
        }
    }

    private static ApplicationPorts application(String modulePath,
                                                String businessPortProperty,
                                                int businessPort,
                                                int managementPort) {
        return new ApplicationPorts(
                modulePath,
                modulePath + "/pom.xml",
                modulePath + "/src/main/resources/application.yml",
                businessPortProperty,
                businessPort,
                managementPort);
    }

    private static List<PropertySource<?>> load(String relativePath) throws Exception {
        return new YamlPropertySourceLoader().load(
                relativePath, new FileSystemResource(REPO_ROOT.resolve(relativePath)));
    }

    private static Object property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    private static Set<String> directDependencyArtifactIds(String relativePom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setNamespaceAware(false);
        Document document = factory.newDocumentBuilder().parse(REPO_ROOT.resolve(relativePom).toFile());
        NodeList dependencies = document.getDocumentElement().getElementsByTagName("dependencies");
        Set<String> result = new HashSet<>();
        if (dependencies.getLength() == 0) {
            return result;
        }
        NodeList children = dependencies.item(0).getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node dependency = children.item(index);
            if (!"dependency".equals(dependency.getNodeName())) {
                continue;
            }
            NodeList fields = dependency.getChildNodes();
            for (int fieldIndex = 0; fieldIndex < fields.getLength(); fieldIndex++) {
                if ("artifactId".equals(fields.item(fieldIndex).getNodeName())) {
                    result.add(fields.item(fieldIndex).getTextContent().trim());
                }
            }
        }
        return result;
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("im-test/im-architecture-test"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root not found");
    }

    private record ApplicationPorts(String modulePath,
                                    String pomPath,
                                    String applicationPath,
                                    String businessPortProperty,
                                    int businessPort,
                                    int managementPort) {
    }
}
