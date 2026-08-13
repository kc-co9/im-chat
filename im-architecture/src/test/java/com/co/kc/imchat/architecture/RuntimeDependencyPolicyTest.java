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
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeDependencyPolicyTest {
    private static final Path REPO_ROOT = Path.of("").toAbsolutePath().getParent();
    private static final Pattern PUBLIC_RECORD = Pattern.compile("\\bpublic\\s+record\\s+\\w+\\s*\\(");

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
