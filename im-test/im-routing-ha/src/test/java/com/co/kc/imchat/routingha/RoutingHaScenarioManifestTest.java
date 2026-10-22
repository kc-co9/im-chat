package com.co.kc.imchat.routingha;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import javax.tools.StandardJavaFileManager;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingHaScenarioManifestTest {

    @Test
    void allTestEvidenceSelectorsResolveToCurrentSourceMethods() throws IOException {
        Path root = Path.of("../..").toAbsolutePath().normalize();
        List<Path> sources;
        try (Stream<Path> files = Files.walk(root)) {
            sources = files.filter(path -> path.toString().contains("/src/test/java/"))
                    .filter(path -> path.toString().endsWith(".java")).toList();
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK compiler is required for source selector validation").isNotNull();
        for (String line : Files.readAllLines(Path.of("src/test/resources/routing-ha-scenarios.tsv"))) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] columns = line.split("\t", -1);
            if (columns[2].equals("topology")) {
                continue;
            }
            String[] selector = columns[3].split("#", 2);
            List<Path> matches = sources.stream()
                    .filter(path -> path.getFileName().toString().equals(selector[0] + ".java")).toList();
            assertThat(matches).as("source for %s", columns[3]).hasSize(1);
            if (selector.length == 1) {
                continue;
            }
            try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, null)) {
                JavacTask parser = (JavacTask) compiler.getTask(null, manager, null,
                        List.of("-proc:none"), null, manager.getJavaFileObjectsFromPaths(matches));
                List<String> methods = new ArrayList<>();
                TreeScanner<Void, Void> scanner = new TreeScanner<>() {
                    @Override
                    public Void visitClass(ClassTree node, Void unused) {
                        return node.getSimpleName().contentEquals(selector[0]) ? super.visitClass(node, unused) : null;
                    }

                    @Override
                    public Void visitMethod(MethodTree node, Void unused) {
                        methods.add(node.getName().toString());
                        return null;
                    }
                };
                parser.parse().forEach(unit -> scanner.scan(unit, null));
                assertThat(methods).as("method for %s", columns[3]).contains(selector[1]);
            }
        }
    }

    @Test
    void automaticFailureEvidenceRequiresTheIsolatedTlsRuntimeRatherThanRecoveryOnly() throws IOException {
        String automatic = Files.readAllLines(Path.of("src/test/resources/routing-ha-scenarios.tsv")).stream()
                .filter(line -> line.startsWith("RH-05\t")).findFirst().orElseThrow();
        String[] columns = automatic.split("\t", -1);
        assertThat(columns[2]).isEqualTo("isolated-runtime-test");
        assertThat(columns[3]).isEqualTo("AutomaticFailureTlsHaTest");
        assertThat(columns[4]).contains("mTLS", "FAIL_BROKER", "Lease");
    }

    @Test
    void definesExactlyTwentyFiveLocatableScenarios() throws IOException {
        Path manifest = Path.of("src/test/resources/routing-ha-scenarios.tsv");
        List<String> lines = Files.readAllLines(manifest).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();

        assertThat(lines).hasSize(25);
        Set<String> ids = new HashSet<>();
        for (String line : lines) {
            String[] columns = line.split("\\t", -1);
            assertThat(columns).as("scenario columns: %s", line).hasSize(5);
            assertThat(columns[0]).matches("RH-(0[1-9]|1[0-9]|2[0-5])");
            assertThat(columns[1]).isNotBlank();
            assertThat(columns[2]).isIn("topology", "broker-test", "gateway-test", "e2e-test", "isolated-runtime-test");
            assertThat(columns[3]).isNotBlank();
            assertThat(columns[4]).isNotBlank();
            assertThat(ids.add(columns[0])).as("unique id %s", columns[0]).isTrue();
        }
    }
}
