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
    private static final Path REPO_ROOT = repositoryRoot();
    private static final String SUREFIRE_ARG_LINE = "@{argLine} -Dfile.encoding=UTF-8 -Djava.awt.headless=true"
            + " -Xshare:off -javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}"
            + "/mockito-core-${mockito.version}.jar";
    private static final Pattern PUBLIC_RECORD = Pattern.compile("\\bpublic\\s+record\\s+\\w+\\s*\\(");
    private static final Pattern SNOWFLAKE_INFRASTRUCTURE_CONSTRUCTION = Pattern.compile(
            "\\bnew\\s+(?:[\\w.]+\\.)?(?:SnowflakeId|StaticSnowflakeMachineId|RedisSnowflakeMachineId)\\s*\\(");

    @Test
    void rootPomLimitsJacocoInstrumentationToPrepareAgentOnly() throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(REPO_ROOT.resolve("pom.xml").toFile());
        assertJacocoConfigurationTree(document, List.of());
    }

    @Test
    void jacocoRootConfigurationFixturesRejectScopeOverrides() throws Exception {
        String configuration = "<configuration><includes><include>com.co.kc.imchat.*</include></includes></configuration>";
        String accepted = "<project><properties><jacoco.version>0.8.13</jacoco.version></properties>"
                + "<build><plugins><plugin><groupId>org.jacoco</groupId>"
                + "<artifactId>jacoco-maven-plugin</artifactId><version>${jacoco.version}</version><executions><execution>"
                + "<goals><goal>prepare-agent</goal></goals>" + configuration
                + "</execution><execution><id>report</id><phase>verify</phase>"
                + "<goals><goal>report</goal></goals></execution></executions>"
                + "</plugin></plugins></build></project>";
        assertJacocoRootConfiguration(parseConfigurationXml(accepted));
        List<String> invalidConfigurations = List.of(
                accepted.replace(configuration, ""),
                accepted.replace("com.co.kc.imchat.*", "com.co.kc.imchat.common.*"),
                accepted.replace(configuration, "").replace("<executions>", configuration + "<executions>"),
                accepted.replace(configuration, "").replace("<id>report</id>", "<id>report</id>" + configuration),
                accepted.replace("</configuration>", "<excludes><exclude>Any</exclude></excludes></configuration>"),
                accepted.replace("</configuration>", "<skip>true</skip></configuration>"),
                accepted.replace("<goal>prepare-agent</goal>", "<goal>prepare-agent</goal><goal>report</goal>"));
        for (int index = 0; index < invalidConfigurations.size(); index++) {
            Document invalid = parseConfigurationXml(invalidConfigurations.get(index));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoRootConfiguration(invalid))
                    .as("root configuration mutation %s", index)
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void childJacocoConfigurationFixturesRejectOverrides() throws Exception {
        Document root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(REPO_ROOT.resolve("pom.xml").toFile());
        assertJacocoConfigurationTree(root, List.of(parseConfigurationXml("<project/>")));
        assertJacocoConfigurationTree(root, List.of(parseConfigurationXml(
                "<project><build><plugins><plugin><groupId>example</groupId>"
                        + "<artifactId>unrelated</artifactId></plugin></plugins></build></project>")));
        Document child = parseConfigurationXml("<project><build><plugins><plugin>"
                + "<groupId>org.jacoco</groupId><artifactId>jacoco-maven-plugin</artifactId>"
                + "<configuration><skip>true</skip></configuration>"
                + "</plugin></plugins></build></project>");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(root, List.of(child)))
                .as("child JaCoCo skip override must be rejected by root and child validation")
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void managedJacocoConfigurationFixturesRejectOverrides() throws Exception {
        String rootXml = Files.readString(REPO_ROOT.resolve("pom.xml"));
        String plugin = "<plugin><groupId>org.jacoco</groupId>"
                + "<artifactId>jacoco-maven-plugin</artifactId><version>0.8.13</version></plugin>";
        List<String> accepted = List.of("", plugin,
                "<plugin><groupId>example</groupId><artifactId>unrelated</artifactId></plugin>");
        List<String> invalid = new ArrayList<>();
        for (String extra : List.of("<configuration><skip>true</skip></configuration>",
                "<configuration><includes><include>Any</include></includes></configuration>",
                "<executions><execution><goals><goal>report</goal></goals></execution></executions>",
                "<dependencies/>", "<extensions>true</extensions>", "<inherited>false</inherited>", "<unknown/>")) {
            invalid.add(plugin.replace("</plugin>", extra + "</plugin>"));
        }
        invalid.add(plugin.replace("0.8.13", "0.8.12"));
        invalid.add(plugin.replace("0.8.13", "${jacoco.version}"));
        invalid.add(plugin.replace("<version>0.8.13</version>", ""));
        invalid.add(plugin + plugin);
        for (String field : List.of("groupId", "artifactId", "version")) {
            String value = switch (field) {
                case "groupId" -> "org.jacoco";
                case "artifactId" -> "jacoco-maven-plugin";
                default -> "0.8.13";
            };
            invalid.add(plugin.replace("</plugin>", "<" + field + ">" + value + "</" + field + "></plugin>"));
            invalid.add(plugin.replace("<" + field + ">" + value + "</" + field + ">",
                    "<" + field + "><nested>" + value + "</nested></" + field + ">"));
        }
        for (boolean rootLocation : List.of(true, false)) {
            for (String declaration : accepted) {
                String management = "<pluginManagement><plugins>" + declaration + "</plugins></pluginManagement>";
                Document root = parseConfigurationXml(rootLocation
                        ? rootXml.replace("</build>", management + "</build>") : rootXml);
                Document child = parseConfigurationXml("<project><build>"
                        + (rootLocation ? "" : management) + "</build></project>");
                assertJacocoConfigurationTree(root, List.of(child));
            }
            for (int index = 0; index < invalid.size(); index++) {
                String management = "<pluginManagement><plugins>" + invalid.get(index)
                        + "</plugins></pluginManagement>";
                Document root = parseConfigurationXml(rootLocation
                        ? rootXml.replace("</build>", management + "</build>") : rootXml);
                Document child = parseConfigurationXml("<project><build>"
                        + (rootLocation ? "" : management) + "</build></project>");
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(root, List.of(child)))
                        .as("managed JaCoCo root=%s mutation=%s", rootLocation, index)
                        .isInstanceOf(AssertionError.class);
            }
        }
    }

    @Test
    void projectProfileFixturesRequireEmptyEntries() throws Exception {
        String rootXml = Files.readString(REPO_ROOT.resolve("pom.xml"));
        List<String> invalidProfiles = List.of(
                "<profile><id>unrelated</id></profile>",
                "<profile><id>version-only</id><build><plugins><plugin><groupId>org.jacoco</groupId>"
                        + "<artifactId>jacoco-maven-plugin</artifactId><version>0.8.13</version>"
                        + "</plugin></plugins></build></profile>",
                "<profile><id>skip</id><build><plugins><plugin><groupId>org.jacoco</groupId>"
                        + "<artifactId>jacoco-maven-plugin</artifactId><configuration><skip>true</skip>"
                        + "</configuration></plugin></plugins></build></profile>",
                "<profile><id>managed</id><build><pluginManagement><plugins><plugin>"
                        + "<groupId>org.jacoco</groupId><artifactId>jacoco-maven-plugin</artifactId>"
                        + "<version>0.8.13</version></plugin></plugins></pluginManagement></build></profile>");
        for (boolean rootLocation : List.of(true, false)) {
            for (String profiles : List.of("", "<profiles/>")) {
                Document root = parseConfigurationXml(rootLocation
                        ? rootXml.replace("</project>", profiles + "</project>") : rootXml);
                Document child = parseConfigurationXml("<project>"
                        + (rootLocation ? "" : profiles) + "</project>");
                assertJacocoConfigurationTree(root, List.of(child));
            }
            for (int index = 0; index < invalidProfiles.size(); index++) {
                String profiles = "<profiles>" + invalidProfiles.get(index) + "</profiles>";
                Document root = parseConfigurationXml(rootLocation
                        ? rootXml.replace("</project>", profiles + "</project>") : rootXml);
                Document child = parseConfigurationXml("<project>"
                        + (rootLocation ? "" : profiles) + "</project>");
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(root, List.of(child)))
                        .as("project profile root=%s mutation=%s", rootLocation, index)
                        .isInstanceOf(AssertionError.class);
            }
        }
    }

    @Test
    void pluginCoordinateFixturesRejectDynamicIdentity() throws Exception {
        Document root = parseConfigurationXml(Files.readString(REPO_ROOT.resolve("pom.xml")));
        Document child = parseConfigurationXml("<project><properties><coverage.group>org.jacoco</coverage.group>"
                + "</properties><build><pluginManagement><plugins><plugin><groupId>${coverage.group}</groupId>"
                + "<artifactId>jacoco-maven-plugin</artifactId><version>0.8.13</version>"
                + "<configuration><skip>true</skip></configuration>"
                + "</plugin></plugins></pluginManagement></build></project>");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(root, List.of(child)))
                .as("expression identity must not hide managed JaCoCo skip")
                .isInstanceOf(AssertionError.class);
        String rootXml = Files.readString(REPO_ROOT.resolve("pom.xml"));
        String plugin = "<plugin><groupId>example</groupId><artifactId>unrelated</artifactId>"
                + "<version>${other.version}</version></plugin>";
        List<String> invalid = new ArrayList<>();
        for (String field : List.of("groupId", "artifactId")) {
            String value = "groupId".equals(field) ? "example" : "unrelated";
            String original = "<" + field + ">" + value + "</" + field + ">";
            for (String replacement : List.of("${coordinate}", "@{coordinate}", "", "bad/value", "<nested>" + value + "</nested>")) {
                invalid.add(plugin.replace(original, "<" + field + ">" + replacement + "</" + field + ">"));
            }
            invalid.add(plugin.replace(original, original + original));
        }
        invalid.add(plugin.replace("<artifactId>unrelated</artifactId>", ""));
        for (boolean rootLocation : List.of(true, false)) {
            for (boolean managed : List.of(true, false)) {
                for (String accepted : List.of(plugin, plugin.replace("<groupId>example</groupId>", ""))) {
                    String build = "<plugins>" + accepted + "</plugins>";
                    if (managed) {
                        build = "<pluginManagement>" + build + "</pluginManagement>";
                    }
                    assertJacocoConfigurationTree(parseConfigurationXml(rootLocation
                                    ? rootXml.replace("</build>", build + "</build>") : rootXml),
                            List.of(parseConfigurationXml("<project><build>" + (rootLocation ? "" : build)
                                    + "</build></project>")));
                }
                for (int index = 0; index < invalid.size(); index++) {
                    String build = "<plugins>" + invalid.get(index) + "</plugins>";
                    if (managed) {
                        build = "<pluginManagement>" + build + "</pluginManagement>";
                    }
                    Document fixtureRoot = parseConfigurationXml(rootLocation
                            ? rootXml.replace("</build>", build + "</build>") : rootXml);
                    Document fixtureChild = parseConfigurationXml("<project><build>"
                            + (rootLocation ? "" : build) + "</build></project>");
                    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                            assertJacocoConfigurationTree(fixtureRoot, List.of(fixtureChild)))
                            .as("identity root=%s managed=%s mutation=%s", rootLocation, managed, index)
                            .isInstanceOf(AssertionError.class);
                }
            }
        }
    }

    @Test
    void jacocoPropertyFixturesRejectIndirectOverrides() throws Exception {
        Document root = parseConfigurationXml(Files.readString(REPO_ROOT.resolve("pom.xml")));
        Document child = parseConfigurationXml("<project><properties><jacoco.skip>true</jacoco.skip>"
                + "</properties></project>");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(root, List.of(child)))
                .as("child JaCoCo skip property must not bypass configuration checks")
                .isInstanceOf(AssertionError.class);
        String rootXml = Files.readString(REPO_ROOT.resolve("pom.xml"));
        for (String properties : List.of("", "<properties/>",
                "<properties><other.version>${project.version}</other.version></properties>")) {
            assertJacocoConfigurationTree(parseConfigurationXml(rootXml),
                    List.of(parseConfigurationXml("<project>" + properties + "</project>")));
        }
        for (boolean rootLocation : List.of(true, false)) {
            for (String key : List.of("jacoco.skip", "jacoco.includes", "jacoco.excludes", "jacoco.destFile",
                    "jacoco.append", "jacoco.propertyName", "jacoco.unknown", "jacoco.version")) {
                String properties = "<properties><" + key + ">0.8.13</" + key + "></properties>";
                Document fixtureRoot = parseConfigurationXml(rootLocation
                        ? rootXml.replace("</project>", properties + "</project>") : rootXml);
                Document fixtureChild = parseConfigurationXml("<project>"
                        + (rootLocation ? "" : properties) + "</project>");
                org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        assertJacocoConfigurationTree(fixtureRoot, List.of(fixtureChild)))
                        .as("indirect property root=%s key=%s", rootLocation, key).isInstanceOf(AssertionError.class);
            }
        }
        String property = "<jacoco.version>0.8.13</jacoco.version>";
        for (String replacement : List.of("", property + property,
                "<jacoco.version>0.8.12</jacoco.version>", "<jacoco.version>${other.version}</jacoco.version>",
                "<jacoco.version><nested>0.8.13</nested></jacoco.version>")) {
            Document invalid = parseConfigurationXml(rootXml.replace(property, replacement));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(invalid, List.of()))
                    .as("invalid root version property %s", replacement).isInstanceOf(AssertionError.class);
        }
        String pluginVersion = "<version>${jacoco.version}</version>";
        for (String replacement : List.of("", pluginVersion + pluginVersion,
                "<version>0.8.13</version>", "<version>${other.version}</version>",
                "<version><nested>${jacoco.version}</nested></version>")) {
            Document invalid = parseConfigurationXml(rootXml.replace(pluginVersion, replacement));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(invalid, List.of()))
                    .as("invalid root plugin version %s", replacement).isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void surefireArgLineFixturesRejectOverrides() throws Exception {
        Document root = parseConfigurationXml(Files.readString(REPO_ROOT.resolve("pom.xml")));
        Document child = parseConfigurationXml("<project><build><plugins><plugin>"
                + "<artifactId>maven-surefire-plugin</artifactId><configuration><argLine>-Xmx64m</argLine>"
                + "</configuration></plugin></plugins></build></project>");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(root, List.of(child)))
                .as("child argLine cannot discard coverage and Mockito agents").isInstanceOf(AssertionError.class);
        String rootXml = Files.readString(REPO_ROOT.resolve("pom.xml"));
        Document mergeChild = parseConfigurationXml("<project><build><plugins><plugin>"
                + "<artifactId>maven-surefire-plugin</artifactId><configuration combine.self=\"override\">"
                + "<additionalClasspathElements><additionalClasspathElement>local/classes</additionalClasspathElement>"
                + "</additionalClasspathElements></configuration></plugin></plugins></build></project>");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                assertJacocoConfigurationTree(parseConfigurationXml(rootXml), List.of(mergeChild)))
                .as("merge control cannot silently remove inherited argLine").isInstanceOf(AssertionError.class);
        assertJacocoConfigurationTree(parseConfigurationXml(rootXml), List.of());
        assertJacocoConfigurationTree(parseConfigurationXml(rootXml), List.of(parseConfigurationXml(
                Files.readString(REPO_ROOT.resolve("im-test/im-architecture-test/pom.xml")))));
        for (boolean rootLocation : List.of(true, false)) {
            for (boolean managed : List.of(true, false)) {
                for (boolean execution : List.of(true, false)) {
                    for (String attribute : List.of("combine.self", "combine.children")) {
                        for (String value : List.of("override", "append", "")) {
                            String configuration = "<configuration " + attribute + "=\"" + value + "\">"
                                    + "<additionalClasspathElements/></configuration>";
                            String body = execution ? "<executions><execution>" + configuration
                                    + "</execution></executions>" : configuration;
                            String plugins = "<plugins><plugin><artifactId>maven-surefire-plugin</artifactId>"
                                    + body + "</plugin></plugins>";
                            String addition = managed ? "<pluginManagement>" + plugins + "</pluginManagement>" : plugins;
                            Document fixtureRoot = parseConfigurationXml(rootLocation
                                    ? rootXml.replace("</build>", addition + "</build>") : rootXml);
                            Document fixtureChild = parseConfigurationXml("<project><build>"
                                    + (rootLocation ? "" : addition) + "</build></project>");
                            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                                    assertJacocoConfigurationTree(fixtureRoot, List.of(fixtureChild)))
                                    .as("merge root=%s managed=%s execution=%s attribute=%s value=%s",
                                            rootLocation, managed, execution, attribute, value)
                                    .isInstanceOf(AssertionError.class);
                        }
                    }
                }
            }
        }
        for (String attribute : List.of("combine.self", "combine.children")) {
            for (boolean execution : List.of(true, false)) {
                Document invalid = parseConfigurationXml(rootXml);
                Element management = directChildren(directChildren(invalid.getDocumentElement(), "build").getFirst(),
                        "pluginManagement").getFirst();
                Element owner = directChildren(directChildren(management, "plugins").getFirst(), "plugin").stream()
                        .filter(plugin -> "maven-surefire-plugin".equals(coverageScalar(plugin, "artifactId")))
                        .findFirst().orElseThrow();
                if (execution) {
                    owner.appendChild(invalid.importNode(parseConfigurationXml("<executions><execution>"
                            + "<configuration " + attribute + "=\"override\"><additionalClasspathElements/>"
                            + "</configuration></execution></executions>").getDocumentElement(), true));
                } else {
                    directChildren(owner, "configuration").getFirst().setAttribute(attribute, "override");
                }
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(invalid, List.of()))
                        .as("root template owner merge attribute=%s execution=%s", attribute, execution)
                        .isInstanceOf(AssertionError.class);
            }
        }
        for (String changed : List.of("", SUREFIRE_ARG_LINE.replace("@{argLine}", "${argLine}"),
                SUREFIRE_ARG_LINE.replace("${settings.localRepository}", "${other.repo}"),
                SUREFIRE_ARG_LINE.replace("${mockito.version}", "0.0.0"),
                SUREFIRE_ARG_LINE + " -javaagent:third.jar", SUREFIRE_ARG_LINE.replace(" -Xshare:off", ""),
                SUREFIRE_ARG_LINE.replace(" -Dfile.encoding=UTF-8", "  -Dfile.encoding=UTF-8"))) {
            Document invalid = parseConfigurationXml(rootXml.replace(SUREFIRE_ARG_LINE, changed));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(invalid, List.of()))
                    .as("complete template mutation %s", changed).isInstanceOf(AssertionError.class);
        }
        String argLine = "<argLine>" + SUREFIRE_ARG_LINE + "</argLine>";
        for (String replacement : List.of("", argLine + argLine,
                "<argLine><nested>" + SUREFIRE_ARG_LINE + "</nested></argLine>")) {
            Document invalid = parseConfigurationXml(rootXml.replace(argLine, replacement));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(invalid, List.of()))
                    .as("argLine structural mutation").isInstanceOf(AssertionError.class);
        }
        String managedPlugin = "<plugin><artifactId>maven-surefire-plugin</artifactId><configuration>"
                + argLine + "</configuration></plugin>";
        Document duplicate = parseConfigurationXml(rootXml.replace("</build>",
                "<pluginManagement><plugins>" + managedPlugin + "</plugins></pluginManagement></build>"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(duplicate, List.of()))
                .as("second managed container cannot duplicate template owner").isInstanceOf(AssertionError.class);
        for (boolean rootLocation : List.of(true, false)) {
            for (boolean managed : List.of(true, false)) {
                for (boolean execution : List.of(true, false)) {
                    for (String field : List.of("argLine", "skip", "skipTests", "skipExec", "maven.test.skip", "forkCount", "forkMode")) {
                        String configuration = "<configuration><" + field + ">false</" + field + "></configuration>";
                        String body = execution ? "<executions><execution>" + configuration
                                + "</execution></executions>" : configuration;
                        String plugins = "<plugins><plugin><artifactId>maven-surefire-plugin</artifactId>"
                                + body + "</plugin></plugins>";
                        String addition = managed ? "<pluginManagement>" + plugins + "</pluginManagement>" : plugins;
                        Document fixtureRoot = parseConfigurationXml(rootLocation
                                ? rootXml.replace("</build>", addition + "</build>") : rootXml);
                        Document fixtureChild = parseConfigurationXml("<project><build>"
                                + (rootLocation ? "" : addition) + "</build></project>");
                        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                                assertJacocoConfigurationTree(fixtureRoot, List.of(fixtureChild)))
                                .as("protected slot root=%s managed=%s execution=%s field=%s",
                                        rootLocation, managed, execution, field).isInstanceOf(AssertionError.class);
                    }
                }
            }
        }
        List<String> ownerOverrides = new ArrayList<>();
        ownerOverrides.add("<configuration><argLine>other</argLine></configuration>");
        for (String field : List.of("argLine", "skip", "skipTests", "skipExec", "maven.test.skip", "forkCount", "forkMode")) {
            ownerOverrides.add("<executions><execution><configuration><" + field + ">false</" + field
                    + "></configuration></execution></executions>");
            if (!"argLine".equals(field)) {
                Document invalid = parseConfigurationXml(rootXml.replace(argLine, argLine + "<" + field + ">false</" + field + ">"));
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(invalid, List.of()))
                        .as("root template owner protected slot %s", field).isInstanceOf(AssertionError.class);
            }
        }
        for (String extra : ownerOverrides) {
            Document invalid = parseConfigurationXml(rootXml);
            Element management = directChildren(directChildren(invalid.getDocumentElement(), "build").getFirst(),
                    "pluginManagement").getFirst();
            Element owner = directChildren(directChildren(management, "plugins").getFirst(), "plugin").stream()
                    .filter(plugin -> "maven-surefire-plugin".equals(coverageScalar(plugin, "artifactId")))
                    .findFirst().orElseThrow();
            owner.appendChild(invalid.importNode(parseConfigurationXml(extra).getDocumentElement(), true));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(invalid, List.of()))
                    .as("second configuration or execution cannot override root template").isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void surefirePropertyFixturesRejectTestBypass() throws Exception {
        Document root = parseConfigurationXml(Files.readString(REPO_ROOT.resolve("pom.xml")));
        Document child = parseConfigurationXml("<project><properties><skipTests>true</skipTests></properties></project>");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(root, List.of(child)))
                .as("child skipTests property must not bypass test evidence").isInstanceOf(AssertionError.class);
        String rootXml = Files.readString(REPO_ROOT.resolve("pom.xml"));
        for (String properties : List.of("", "<properties/>",
                "<properties><ordinary.version>${project.version}</ordinary.version>"
                        + "<mockito.version>5.17.0</mockito.version></properties>")) {
            assertJacocoConfigurationTree(parseConfigurationXml(rootXml),
                    List.of(parseConfigurationXml("<project>" + properties + "</project>")));
        }
        for (boolean rootLocation : List.of(true, false)) {
            for (String name : List.of("argLine", "maven.test.skip", "maven.test.skip.exec", "skipTests", "skipExec",
                    "forkCount", "forkMode", "surefire.argLine", "surefire.debug", "surefire.unknown")) {
                for (boolean secondContainer : List.of(true, false)) {
                    String properties = (secondContainer ? "<properties><ordinary.version>1</ordinary.version></properties>" : "")
                            + "<properties><" + name + ">false</" + name + "></properties>";
                    Document fixtureRoot = parseConfigurationXml(rootLocation
                            ? rootXml.replace("</project>", properties + "</project>") : rootXml);
                    Document fixtureChild = parseConfigurationXml("<project>"
                            + (rootLocation ? "" : properties) + "</project>");
                    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                            assertJacocoConfigurationTree(fixtureRoot, List.of(fixtureChild)))
                            .as("protected property root=%s second=%s key=%s", rootLocation, secondContainer, name)
                            .isInstanceOf(AssertionError.class);
                }
            }
        }
    }

    @Test
    void compilerProcessorFixturesRejectOverrides() throws Exception {
        Document root = parseConfigurationXml(Files.readString(REPO_ROOT.resolve("pom.xml")));
        Document child = parseConfigurationXml("<project><build><plugins><plugin>"
                + "<artifactId>maven-compiler-plugin</artifactId><configuration><annotationProcessorPaths><path>"
                + "<groupId>third.party</groupId><artifactId>processor</artifactId><version>1</version>"
                + "</path></annotationProcessorPaths></configuration></plugin></plugins></build></project>");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(root, List.of(child)))
                .as("child third-party annotation processor cannot bypass owned compilation contract")
                .isInstanceOf(AssertionError.class);
        String rootXml = Files.readString(REPO_ROOT.resolve("pom.xml"));
        Document reordered = parseConfigurationXml(rootXml);
        Element configuration = compilerOwnerConfiguration(reordered);
        Element paths = directChildren(configuration, "annotationProcessorPaths").getFirst();
        paths.appendChild(directChildren(paths, "path").getFirst());
        assertJacocoConfigurationTree(reordered, List.of());
        for (boolean rootLocation : List.of(true, false)) {
            for (boolean managed : List.of(true, false)) {
                String declaration = "<plugin><artifactId>maven-compiler-plugin</artifactId><version>${other.version}</version></plugin>";
                String plugins = "<plugins>" + declaration + "</plugins>";
                String addition = managed ? "<pluginManagement>" + plugins + "</pluginManagement>" : plugins;
                if (!rootLocation || !managed) {
                    assertJacocoConfigurationTree(parseConfigurationXml(rootLocation
                                    ? rootXml.replace("</build>", addition + "</build>") : rootXml),
                            List.of(parseConfigurationXml("<project><build>" + (rootLocation ? "" : addition) + "</build></project>")));
                }
                for (boolean execution : List.of(true, false)) {
                    String override = "<configuration><annotationProcessorPaths/></configuration>";
                    String body = execution ? "<executions><execution>" + override + "</execution></executions>" : override;
                    String changedPlugins = "<plugins>" + declaration.replace("</plugin>", body + "</plugin>") + "</plugins>";
                    String changed = managed ? "<pluginManagement>" + changedPlugins + "</pluginManagement>" : changedPlugins;
                    Document fixtureRoot = parseConfigurationXml(rootLocation
                            ? rootXml.replace("</build>", changed + "</build>") : rootXml);
                    Document fixtureChild = parseConfigurationXml("<project><build>" + (rootLocation ? "" : changed) + "</build></project>");
                    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                            assertJacocoConfigurationTree(fixtureRoot, List.of(fixtureChild)))
                            .as("compiler override root=%s managed=%s execution=%s", rootLocation, managed, execution)
                            .isInstanceOf(AssertionError.class);
                }
            }
        }
        List<String> extraFields = List.of("annotationProcessors", "compilerArgs", "generatedSourcesDirectory", "proc", "fork", "compilerId", "executable");
        for (String field : extraFields) {
            Document invalid = parseConfigurationXml(rootXml);
            compilerOwnerConfiguration(invalid).appendChild(invalid.createElement(field));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(invalid, List.of()))
                    .as("unknown owner compiler field %s", field).isInstanceOf(AssertionError.class);
        }
        for (String target : List.of("configuration", "release", "parameters", "annotationProcessorPaths", "path", "groupId", "artifactId", "version")) {
            for (String attribute : List.of("combine.self", "combine.children")) {
                Document invalid = parseConfigurationXml(rootXml);
                Element ownerConfiguration = compilerOwnerConfiguration(invalid);
                Element selected = "configuration".equals(target) ? ownerConfiguration
                        : (Element) ownerConfiguration.getElementsByTagName(target).item(0);
                selected.setAttribute(attribute, "append");
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(invalid, List.of()))
                        .as("compiler merge %s %s", target, attribute).isInstanceOf(AssertionError.class);
            }
        }
        for (String target : List.of("release", "parameters", "annotationProcessorPaths", "path", "groupId", "artifactId", "version")) {
            for (String mutation : List.of("remove", "duplicate", "nested", "wrong", "extra")) {
                Document invalid = parseConfigurationXml(rootXml);
                Element ownerConfiguration = compilerOwnerConfiguration(invalid);
                Element selected = (Element) ownerConfiguration.getElementsByTagName(target).item(0);
                switch (mutation) {
                    case "remove" -> selected.getParentNode().removeChild(selected);
                    case "duplicate" -> selected.getParentNode().appendChild(selected.cloneNode(true));
                    case "nested" -> selected.appendChild(invalid.createElement("nested"));
                    case "wrong" -> selected.setTextContent("unknown");
                    default -> selected.getParentNode().appendChild(invalid.createElement("unknown"));
                }
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(invalid, List.of()))
                        .as("compiler shape %s mutation %s", target, mutation).isInstanceOf(AssertionError.class);
            }
        }
        for (String mutation : List.of("duplicateConfiguration", "ownerExecution", "extraProcessor")) {
            Document invalid = parseConfigurationXml(rootXml);
            Element ownerConfiguration = compilerOwnerConfiguration(invalid);
            if ("duplicateConfiguration".equals(mutation)) {
                ownerConfiguration.getParentNode().appendChild(ownerConfiguration.cloneNode(true));
            } else if ("ownerExecution".equals(mutation)) {
                ownerConfiguration.getParentNode().appendChild(invalid.importNode(parseConfigurationXml(
                        "<executions><execution><configuration/></execution></executions>").getDocumentElement(), true));
            } else {
                Element ownerPaths = directChildren(ownerConfiguration, "annotationProcessorPaths").getFirst();
                ownerPaths.appendChild(invalid.importNode(parseConfigurationXml("<path><groupId>third.party</groupId>"
                        + "<artifactId>processor</artifactId><version>1</version></path>").getDocumentElement(), true));
            }
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(invalid, List.of()))
                    .as("owner compiler mutation %s", mutation).isInstanceOf(AssertionError.class);
        }
    }

    private static Element compilerOwnerConfiguration(Document root) {
        return directChildren(root.getDocumentElement(), "build").stream()
                .flatMap(build -> directChildren(build, "pluginManagement").stream())
                .flatMap(management -> directChildren(management, "plugins").stream())
                .flatMap(plugins -> directChildren(plugins, "plugin").stream())
                .filter(plugin -> "maven-compiler-plugin".equals(coverageScalar(plugin, "artifactId")))
                .flatMap(plugin -> directChildren(plugin, "configuration").stream()).findFirst().orElseThrow();
    }

    @Test
    void compilerPropertyFixturesRejectIndirectOverrides() throws Exception {
        Document root = parseConfigurationXml(Files.readString(REPO_ROOT.resolve("pom.xml")));
        Document child = parseConfigurationXml("<project><properties><mapstruct.version>9.9</mapstruct.version>"
                + "</properties></project>");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(root, List.of(child)))
                .as("child processor version property cannot replace approved MapStruct").isInstanceOf(AssertionError.class);
        String rootXml = Files.readString(REPO_ROOT.resolve("pom.xml"));
        assertJacocoConfigurationTree(parseConfigurationXml(rootXml), List.of(parseConfigurationXml(
                "<project><properties><business.version>${project.version}</business.version></properties></project>")));
        for (String key : List.of("maven.compiler.release", "lombok.version", "mapstruct.version")) {
            String value = switch (key) {
                case "maven.compiler.release" -> "21";
                case "lombok.version" -> "1.18.42";
                default -> "1.5.5.Final";
            };
            String property = "<" + key + ">" + value + "</" + key + ">";
            for (String replacement : List.of("", property + property, "<" + key + ">wrong</" + key + ">",
                    "<" + key + ">${other.version}</" + key + ">", "<" + key + "><nested>" + value + "</nested></" + key + ">")) {
                Document invalid = parseConfigurationXml(rootXml.replace(property, replacement));
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(invalid, List.of()))
                        .as("root compiler property %s mutation %s", key, replacement).isInstanceOf(AssertionError.class);
            }
            Document duplicate = parseConfigurationXml(rootXml.replace("</project>", "<properties>" + property + "</properties></project>"));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(duplicate, List.of()))
                    .as("second property container %s", key).isInstanceOf(AssertionError.class);
            Document fixtureChild = parseConfigurationXml("<project><properties/><properties>" + property + "</properties></project>");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(root, List.of(fixtureChild)))
                    .as("child compiler property %s", key).isInstanceOf(AssertionError.class);
        }
        for (boolean rootLocation : List.of(true, false)) {
            for (String key : List.of("maven.compiler.source", "maven.compiler.target", "maven.compiler.compilerArgs",
                    "maven.compiler.annotationProcessorPaths", "maven.compiler.unknown")) {
                String properties = "<properties><" + key + ">unknown</" + key + "></properties>";
                Document fixtureRoot = parseConfigurationXml(rootLocation
                        ? rootXml.replace("</project>", properties + "</project>") : rootXml);
                Document fixtureChild = parseConfigurationXml("<project>" + (rootLocation ? "" : properties) + "</project>");
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> assertJacocoConfigurationTree(fixtureRoot, List.of(fixtureChild)))
                        .as("unknown compiler property root=%s key=%s", rootLocation, key).isInstanceOf(AssertionError.class);
            }
        }
    }

    @Test
    void reactorChildPomsDoNotOverrideJacocoAgentAndReport() throws Exception {
        List<Document> documents = new ArrayList<>();
        collectDefaultReactorPoms(REPO_ROOT.toRealPath(), REPO_ROOT.resolve("pom.xml"),
                new java.util.HashSet<>(), documents);
        assertThat(documents).isNotEmpty();
        assertJacocoConfigurationTree(documents.getFirst(), documents.subList(1, documents.size()));
    }

    private static void collectDefaultReactorPoms(Path repository, Path pom,
            java.util.Set<Path> visited, List<Document> documents) throws Exception {
        Path canonical = pom.toRealPath();
        assertThat(canonical.startsWith(repository)).as("reactor POM stays inside repository").isTrue();
        assertThat(canonical).as("reactor POM cannot traverse symlinks").isEqualTo(pom.toAbsolutePath().normalize());
        assertThat(visited.add(canonical)).as("unique reactor POM: %s", canonical).isTrue();
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(canonical.toFile());
        documents.add(document);
        for (Element modules : directChildren(document.getDocumentElement(), "modules")) {
            for (Element module : directChildren(modules, "module")) {
                String relative = module.getTextContent().trim();
                assertThat(relative).as("finite literal module path")
                        .matches("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*");
                collectDefaultReactorPoms(repository, canonical.getParent().resolve(relative).resolve("pom.xml"),
                        visited, documents);
            }
        }
    }

    private static void assertJacocoConfigurationTree(Document root, List<Document> children) {
        List<Document> identityDocuments = new ArrayList<>();
        identityDocuments.add(root);
        identityDocuments.addAll(children);
        for (Document document : identityDocuments) {
            List<Element> coverageProperties = new ArrayList<>();
            for (Element properties : directChildren(document.getDocumentElement(), "properties")) {
                NodeList nodes = properties.getChildNodes();
                for (int index = 0; index < nodes.getLength(); index++) {
                    if (nodes.item(index) instanceof Element property) {
                        String name = property.getTagName();
                        assertThat(name.startsWith("surefire.") || List.of("argLine", "maven.test.skip",
                                "maven.test.skip.exec", "skipTests", "skipExec", "forkCount", "forkMode").contains(name))
                                .as("project property cannot override protected Surefire input %s", name).isFalse();
                        if (name.startsWith("jacoco.")) {
                            coverageProperties.add(property);
                        }
                    }
                }
            }
            if (document == root) {
                assertThat(coverageProperties).as("root has only the unique JaCoCo version property").hasSize(1);
                Element version = coverageProperties.getFirst();
                assertThat(version.getTagName()).isEqualTo("jacoco.version");
                assertThat(elementNames(version)).isEmpty();
                assertThat(version.getTextContent().trim()).isEqualTo("0.8.13");
            } else {
                assertThat(coverageProperties).as("children cannot rewrite JaCoCo properties").isEmpty();
            }
            for (Element build : directChildren(document.getDocumentElement(), "build")) {
                List<Element> containers = new ArrayList<>(directChildren(build, "plugins"));
                for (Element management : directChildren(build, "pluginManagement")) {
                    containers.addAll(directChildren(management, "plugins"));
                }
                for (Element plugins : containers) {
                    for (Element plugin : directChildren(plugins, "plugin")) {
                        assertThat(directChildren(plugin, "artifactId")).hasSize(1);
                        assertThat(coverageScalar(plugin, "artifactId")).matches("[A-Za-z0-9_.-]+");
                        assertThat(directChildren(plugin, "groupId")).hasSizeLessThanOrEqualTo(1);
                        if (!directChildren(plugin, "groupId").isEmpty()) {
                            assertThat(coverageScalar(plugin, "groupId")).matches("[A-Za-z0-9_.-]+");
                        }
                    }
                }
            }
        }
        assertSurefireProtectedConfiguration(root, children);
        assertCompilerProcessorConfiguration(root, children);
        assertJacocoRootConfiguration(root);
        List<Document> documents = new ArrayList<>();
        documents.add(root);
        documents.addAll(children);
        for (Document document : documents) {
            for (Element profiles : directChildren(document.getDocumentElement(), "profiles")) {
                assertThat(directChildren(profiles, "profile"))
                        .as("project profile entries must remain empty; settings profiles are separate").isEmpty();
            }
            List<Element> managedJacoco = new ArrayList<>();
            for (Element build : directChildren(document.getDocumentElement(), "build")) {
                for (Element management : directChildren(build, "pluginManagement")) {
                    for (Element plugins : directChildren(management, "plugins")) {
                        managedJacoco.addAll(directChildren(plugins, "plugin").stream()
                                .filter(plugin -> directChildren(plugin, "groupId").stream()
                                        .anyMatch(group -> "org.jacoco".equals(group.getTextContent().trim()))
                                        && directChildren(plugin, "artifactId").stream().anyMatch(artifact ->
                                                "jacoco-maven-plugin".equals(artifact.getTextContent().trim())))
                                .toList());
                    }
                }
            }
            assertThat(managedJacoco).as("at most one managed JaCoCo declaration per POM").hasSizeLessThanOrEqualTo(1);
            for (Element plugin : managedJacoco) {
                assertThat(elementNames(plugin)).as("managed JaCoCo is G:A:version-only")
                        .containsExactlyInAnyOrder("groupId", "artifactId", "version");
                for (String field : List.of("groupId", "artifactId", "version")) {
                    assertThat(directChildren(plugin, field)).hasSize(1);
                    assertThat(elementNames(directChildren(plugin, field).getFirst())).isEmpty();
                }
                assertThat(childText(plugin, "version")).isEqualTo("0.8.13");
            }
        }
        for (Document child : children) {
            for (Element build : directChildren(child.getDocumentElement(), "build")) {
                for (Element plugins : directChildren(build, "plugins")) {
                    assertThat(directChildren(plugins, "plugin").stream()
                            .filter(plugin -> "org.jacoco".equals(childText(plugin, "groupId"))
                                    && "jacoco-maven-plugin".equals(childText(plugin, "artifactId")))
                            .toList()).as("child must inherit root JaCoCo without redeclaration").isEmpty();
                }
            }
        }
    }

    private static void assertCompilerProcessorConfiguration(Document root, List<Document> children) {
        List<Document> documents = new ArrayList<>();
        documents.add(root);
        documents.addAll(children);
        List<Element> owners = new ArrayList<>();
        for (Document document : documents) {
            for (String key : List.of("maven.compiler.release", "lombok.version", "mapstruct.version")) {
                List<Element> properties = directChildren(document.getDocumentElement(), "properties").stream()
                        .flatMap(container -> directChildren(container, key).stream()).toList();
                if (document == root) {
                    assertThat(properties).as("unique root compiler property %s", key).hasSize(1);
                    assertThat(elementNames(properties.getFirst())).isEmpty();
                    String expected = switch (key) {
                        case "maven.compiler.release" -> "21";
                        case "lombok.version" -> "1.18.42";
                        default -> "1.5.5.Final";
                    };
                    assertThat(properties.getFirst().getTextContent().trim()).isEqualTo(expected);
                } else {
                    assertThat(properties).as("child cannot rewrite compiler property %s", key).isEmpty();
                }
            }
            for (Element properties : directChildren(document.getDocumentElement(), "properties")) {
                for (String name : elementNames(properties)) {
                    assertThat(name.startsWith("maven.compiler.") && !"maven.compiler.release".equals(name))
                            .as("unsupported compiler property %s", name).isFalse();
                }
            }
            for (Element build : directChildren(document.getDocumentElement(), "build")) {
                List<Element> managed = new ArrayList<>();
                for (Element management : directChildren(build, "pluginManagement")) {
                    managed.addAll(directChildren(management, "plugins"));
                }
                List<Element> containers = new ArrayList<>(directChildren(build, "plugins"));
                containers.addAll(managed);
                for (Element container : containers) {
                    for (Element plugin : directChildren(container, "plugin")) {
                        if ((!directChildren(plugin, "groupId").isEmpty()
                                && !"org.apache.maven.plugins".equals(coverageScalar(plugin, "groupId")))
                                || !"maven-compiler-plugin".equals(coverageScalar(plugin, "artifactId"))) {
                            continue;
                        }
                        List<Element> configurations = directChildren(plugin, "configuration");
                        if (document == root && managed.contains(container)) {
                            owners.add(plugin);
                            assertThat(configurations).hasSize(1);
                            Element configuration = configurations.getFirst();
                            assertNoCoverageMergeControls(configuration);
                            assertThat(elementNames(configuration)).containsExactlyInAnyOrder(
                                    "release", "parameters", "annotationProcessorPaths");
                            assertThat(coverageScalar(configuration, "release")).isEqualTo("${maven.compiler.release}");
                            assertThat(coverageScalar(configuration, "parameters")).isEqualTo("true");
                            for (String field : List.of("release", "parameters")) {
                                assertNoCoverageMergeControls(directChildren(configuration, field).getFirst());
                            }
                            List<Element> containersOfPaths = directChildren(configuration, "annotationProcessorPaths");
                            assertThat(containersOfPaths).hasSize(1);
                            Element paths = containersOfPaths.getFirst();
                            assertNoCoverageMergeControls(paths);
                            assertThat(elementNames(paths)).containsExactly("path", "path");
                            List<String> processors = new ArrayList<>();
                            for (Element path : directChildren(paths, "path")) {
                                assertNoCoverageMergeControls(path);
                                assertThat(elementNames(path)).containsExactlyInAnyOrder("groupId", "artifactId", "version");
                                for (String field : List.of("groupId", "artifactId", "version")) {
                                    assertNoCoverageMergeControls(directChildren(path, field).getFirst());
                                }
                                processors.add(coverageScalar(path, "groupId") + ":" + coverageScalar(path, "artifactId")
                                        + ":" + coverageScalar(path, "version"));
                            }
                            assertThat(processors).containsExactlyInAnyOrder(
                                    "org.projectlombok:lombok:${lombok.version}",
                                    "org.mapstruct:mapstruct-processor:${mapstruct.version}");
                        } else {
                            assertThat(configurations).as("compiler configuration belongs only to root managed owner").isEmpty();
                        }
                        for (Element executions : directChildren(plugin, "executions")) {
                            for (Element execution : directChildren(executions, "execution")) {
                                assertThat(directChildren(execution, "configuration"))
                                        .as("compiler execution configuration is unsupported").isEmpty();
                            }
                        }
                    }
                }
            }
        }
        assertThat(owners).as("unique root managed compiler owner").hasSize(1);
    }

    private static void assertNoCoverageMergeControls(Element element) {
        assertThat(element.hasAttribute("combine.self") || element.hasAttribute("combine.children"))
                .as("coverage configuration merge controls are unsupported").isFalse();
    }

    private static void assertSurefireProtectedConfiguration(Document root, List<Document> children) {
        List<Document> documents = new ArrayList<>();
        documents.add(root);
        documents.addAll(children);
        List<Element> rootManaged = new ArrayList<>();
        for (Document document : documents) {
            for (Element build : directChildren(document.getDocumentElement(), "build")) {
                List<Element> containers = new ArrayList<>(directChildren(build, "plugins"));
                List<Element> managedContainers = new ArrayList<>();
                for (Element management : directChildren(build, "pluginManagement")) {
                    managedContainers.addAll(directChildren(management, "plugins"));
                }
                containers.addAll(managedContainers);
                for (Element container : containers) {
                    for (Element plugin : directChildren(container, "plugin")) {
                        boolean defaultGroup = directChildren(plugin, "groupId").isEmpty()
                                || "org.apache.maven.plugins".equals(coverageScalar(plugin, "groupId"));
                        if (!defaultGroup || !"maven-surefire-plugin".equals(coverageScalar(plugin, "artifactId"))) {
                            continue;
                        }
                        boolean ownsTemplate = document == root && managedContainers.contains(container);
                        List<Element> configurations = new ArrayList<>(directChildren(plugin, "configuration"));
                        if (ownsTemplate) {
                            rootManaged.add(plugin);
                            assertThat(configurations).as("root managed Surefire has one configuration").hasSize(1);
                            assertThat(coverageScalar(configurations.getFirst(), "argLine")).isEqualTo(SUREFIRE_ARG_LINE);
                        }
                        for (Element executions : directChildren(plugin, "executions")) {
                            for (Element execution : directChildren(executions, "execution")) {
                                configurations.addAll(directChildren(execution, "configuration"));
                            }
                        }
                        for (Element configuration : configurations) {
                            assertThat(configuration.hasAttribute("combine.self") || configuration.hasAttribute("combine.children"))
                                    .as("Surefire configuration merge controls are unsupported").isFalse();
                            if (!ownsTemplate || configuration != directChildren(plugin, "configuration").getFirst()) {
                                assertThat(directChildren(configuration, "argLine"))
                                        .as("only root managed plugin owns argLine").isEmpty();
                            }
                            for (String field : List.of("skip", "skipTests", "skipExec", "maven.test.skip", "forkCount", "forkMode")) {
                                assertThat(directChildren(configuration, field))
                                        .as("protected Surefire slot %s cannot be overridden", field).isEmpty();
                            }
                        }
                    }
                }
            }
        }
        assertThat(rootManaged).as("unique root managed Surefire").hasSize(1);
    }

    private static String coverageScalar(Element parent, String name) {
        List<Element> fields = directChildren(parent, name);
        assertThat(fields).as("unique direct coverage field %s", name).hasSize(1);
        assertThat(elementNames(fields.getFirst())).as("plain coverage field %s", name).isEmpty();
        return fields.getFirst().getTextContent().trim();
    }

    private static Document parseConfigurationXml(String source) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(source)));
    }

    private static void assertJacocoRootConfiguration(Document document) {
        List<Element> plugins = directChildren(document.getDocumentElement(), "build").stream()
                .flatMap(build -> directChildren(build, "plugins").stream())
                .flatMap(container -> directChildren(container, "plugin").stream())
                .filter(plugin -> "org.jacoco".equals(childText(plugin, "groupId"))
                        && "jacoco-maven-plugin".equals(childText(plugin, "artifactId")))
                .toList();
        assertThat(plugins).as("unique root JaCoCo plugin").hasSize(1);
        Element plugin = plugins.getFirst();
        assertThat(coverageScalar(plugin, "version")).isEqualTo("${jacoco.version}");
        assertThat(directChildren(plugin, "configuration")).as("no global agent/report overrides").isEmpty();
        List<Element> executions = directChildren(directChildren(plugin, "executions").getFirst(), "execution");
        List<Element> prepareExecutions = executions.stream()
                .filter(execution -> directChildren(execution, "goals").stream()
                        .flatMap(goals -> directChildren(goals, "goal").stream())
                        .anyMatch(goal -> "prepare-agent".equals(goal.getTextContent().trim())))
                .toList();
        assertThat(prepareExecutions).as("unique prepare-agent execution").hasSize(1);
        for (Element execution : executions) {
            List<Element> configurations = directChildren(execution, "configuration");
            if (execution != prepareExecutions.getFirst()) {
                assertThat(configurations).as("report and other executions have no overrides").isEmpty();
                continue;
            }
            List<Element> goals = directChildren(execution, "goals");
            assertThat(goals).as("prepare-agent has one goals container").hasSize(1);
            assertThat(elementNames(goals.getFirst())).containsExactly("goal");
            assertThat(directChildren(goals.getFirst(), "goal").stream()
                    .map(goal -> goal.getTextContent().trim()).toList())
                    .as("namespace include belongs only to prepare-agent, never report")
                    .containsExactly("prepare-agent");
            assertThat(configurations).as("prepare-agent requires namespace configuration").hasSize(1);
            Element configuration = configurations.getFirst();
            assertThat(elementNames(configuration)).containsExactly("includes");
            List<Element> includes = directChildren(configuration, "includes");
            assertThat(includes).hasSize(1);
            assertThat(elementNames(includes.getFirst())).containsExactly("include");
            assertThat(directChildren(includes.getFirst(), "include").getFirst().getTextContent().trim())
                    .isEqualTo("com.co.kc.imchat.*");
        }
    }

    private static List<Element> directChildren(Element parent, String name) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            if (nodes.item(index) instanceof Element element && name.equals(element.getTagName())) {
                children.add(element);
            }
        }
        return children;
    }

    private static List<String> elementNames(Element parent) {
        List<String> names = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            if (nodes.item(index) instanceof Element element) {
                names.add(element.getTagName());
            }
        }
        return names;
    }

    @Test
    void managementModulesIncludeIamAndIndependentApplications() throws Exception {
        Path rootPom = REPO_ROOT.resolve("pom.xml");
        Path managementPom = REPO_ROOT.resolve("im-management/pom.xml");
        Path adminPom = REPO_ROOT.resolve("im-management/im-admin/pom.xml");
        Path opsPom = REPO_ROOT.resolve("im-management/im-ops/pom.xml");
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
        assertThat(opsPom).exists();
        assertThat(iamPom).exists();
        assertThat(auditPom).exists();
        assertThat(moduleNames(managementPom))
                .containsExactlyInAnyOrder("im-iam", "im-admin", "im-ops", "im-audit", "im-ops-cli");
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
        assertThat(hasDependency(opsPom, "com.co.kc.im", "im-broker-server"))
                .isFalse();
        assertThat(hasDependency(adminPom, "com.co.kc.im", "im-iam-sdk")).isTrue();
        assertThat(hasDependency(opsPom, "com.co.kc.im", "im-iam-sdk")).isTrue();
        assertThat(List.of(adminPom, auditServerPom, iamServerPom, opsPom))
                .allMatch(pom -> hasDependency(pom, "com.co.kc.im", "im-nacos"));
    }

    @Test
    void opsCliRuntimeDependenciesStayWithinApprovedJsonLibrary() throws Exception {
        Path cliPom = REPO_ROOT.resolve("im-management/im-ops-cli/pom.xml");
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(cliPom.toFile());
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int index = 0; index < dependencies.getLength(); index++) {
            Element dependency = (Element) dependencies.item(index);
            if (!"test".equals(childText(dependency, "scope"))) {
                String groupId = childText(dependency, "groupId");
                String artifactId = childText(dependency, "artifactId");
                assertThat(("com.co.kc.im".equals(groupId) && "im-common".equals(artifactId))
                        || ("com.fasterxml.jackson.core".equals(groupId)
                        && "jackson-databind".equals(artifactId))).isTrue();
            }
        }
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
                REPO_ROOT.resolve("im-management/im-ops/src/main/java"));
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
        Path opsPom = REPO_ROOT.resolve("im-management/im-ops/pom.xml");

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
        assertThat(hasDependency(opsPom, "com.co.kc.im", "im-account-admin-facade"))
                .isFalse();
        assertThat(packaging(adminPom)).isNotEqualTo("pom");
    }

    @Test
    void onlyDatasourcePluginIncludesH2OutsideTestScope() throws Exception {
        List<Path> poms;
        try (var paths = Files.walk(REPO_ROOT)) {
            poms = paths
                    .filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .toList();
        }

        assertThat(poms)
                .filteredOn(RuntimeDependencyPolicyTest::hasRuntimeH2Dependency)
                .containsExactly(REPO_ROOT.resolve("im-plugin/im-datasource/pom.xml"));
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
        Path opsPom = REPO_ROOT.resolve("im-management/im-ops/pom.xml");
        Path iamSdkPom = REPO_ROOT.resolve("im-management/im-iam/im-iam-sdk/pom.xml");

        assertThat(hasDependency(webPom, "com.co.kc.im", "im-session")).isFalse();
        assertThat(hasDependency(adminPom, "com.co.kc.im", "im-web")).isTrue();
        assertThat(hasDependency(opsPom, "com.co.kc.im", "im-web")).isTrue();
        assertThat(hasDependency(iamSdkPom, "com.co.kc.im", "im-web")).isTrue();
    }

    @Test
    void deployableServletApplicationsConfigureOpenApiPaths() throws IOException {
        List<Path> applicationConfigs = List.of(
                REPO_ROOT.resolve("im-broker/im-broker-server/src/main/resources/application.yml"),
                REPO_ROOT.resolve("im-management/im-admin/src/main/resources/application.yml"),
                REPO_ROOT.resolve("im-management/im-audit/im-audit-server/src/main/resources/application.yml"),
                REPO_ROOT.resolve("im-management/im-iam/im-iam-server/src/main/resources/application.yml"),
                REPO_ROOT.resolve("im-management/im-ops/src/main/resources/application.yml"),
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
                18040);
        assertManagementPort(
                "im-management/im-audit/im-audit-server/src/main/resources/application.yml",
                18041);
        assertManagementPort(
                "im-management/im-ops/src/main/resources/application.yml",
                18043);
        assertManagementPort(
                "im-management/im-admin/src/main/resources/application.yml",
                18042);
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

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        return current.endsWith("im-architecture-test") ? current.getParent().getParent() : current;
    }
}
