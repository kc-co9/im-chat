package com.co.kc.imchat.architecture;

import com.co.kc.imchat.common.utils.NestedMapUtils;
import com.co.kc.imchat.management.ops.model.cqrs.dto.BrokerOperationDTO;
import com.co.kc.imchat.management.ops.model.io.OpsCliSubmissionResponse;
import com.co.kc.imchat.management.ops.transformer.interfaces.OpsCliHttpTransformer;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Select;
import org.jacoco.agent.rt.IAgent;
import org.jacoco.agent.rt.RT;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CoverageInstrumentationTest {

    @Test
    void ownedSourceAndGeneratedMapperHaveProbesButExternalParserDoesNot() throws Exception {
        List<String> arguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
        List<String> agents = arguments.stream().filter(argument -> argument.startsWith("-javaagent:")).toList();
        assertThat(agents).hasSize(2);
        assertThat(arguments).contains("-Dfile.encoding=UTF-8", "-Djava.awt.headless=true", "-Xshare:off");
        Path mockito = Path.of(Mockito.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertThat(agents).contains("-javaagent:" + mockito);
        String jacoco = agents.stream().filter(argument -> argument.contains("org.jacoco.agent-")).findFirst().orElseThrow();
        assertThat(jacoco).contains("org.jacoco.agent-0.8.13-runtime.jar=");
        Map<String, String> options = new HashMap<>();
        for (String option : jacoco.substring(jacoco.indexOf('=') + 1).split(",")) {
            String[] pair = option.split("=", 2);
            assertThat(pair).hasSize(2);
            assertThat(options.put(pair[0], pair[1])).as("duplicate JaCoCo option").isNull();
        }
        assertThat(options.get("includes")).isEqualTo("com.co.kc.imchat.*");
        String destination = options.get("destfile");
        assertThat(destination).isNotBlank();
        Path exec = Path.of(destination).toAbsolutePath().normalize();
        assertThat(exec).isEqualTo(Path.of(System.getProperty("jacoco.destFile", "target/jacoco.exec")).toAbsolutePath().normalize());

        Map<String, Map<String, String>> nested = new HashMap<>();
        nested.put("first", new HashMap<>(Map.of("second", "value")));
        NestedMapUtils.prune(nested, "first", "second");
        assertThat(nested).isEmpty();
        OpsCliSubmissionResponse submission = OpsCliHttpTransformer.INSTANCE.submissionResponseFrom(
                new BrokerOperationDTO("coverage-op", "APPLIED", 1L, null, null, null));
        assertThat(submission.operationId()).isEqualTo("coverage-op");
        assertThat(submission.status()).isEqualTo("APPLIED");
        Select select = (Select) CCJSqlParserUtil.parse("SELECT 1");
        assertThat(select.getPlainSelect().getSelectItem(0).getExpression()).isInstanceOf(LongValue.class);
        assertThat(((LongValue) select.getPlainSelect().getSelectItem(0).getExpression()).getValue()).isEqualTo(1L);

        IAgent agent = RT.getAgent();
        ExecutionDataStore execution = new ExecutionDataStore();
        SessionInfoStore sessions = new SessionInfoStore();
        ExecutionDataReader reader = new ExecutionDataReader(new ByteArrayInputStream(agent.getExecutionData(false)));
        reader.setExecutionDataVisitor(execution);
        reader.setSessionInfoVisitor(sessions);
        while (reader.read()) {
        }
        for (String owned : List.of("com/co/kc/imchat/common/utils/NestedMapUtils",
                "com/co/kc/imchat/management/ops/transformer/interfaces/OpsCliHttpTransformerImpl")) {
            ExecutionData record = execution.getContents().stream().filter(entry -> entry.getName().equals(owned)).findFirst().orElseThrow();
            boolean covered = false;
            for (boolean probe : record.getProbes()) {
                covered |= probe;
            }
            assertThat(covered).as(owned).isTrue();
        }
        assertThat(execution.getContents()).noneMatch(entry -> entry.getName().equals("com/co/kc/imchat/common/utils/HashUtils")
                || entry.getName().startsWith("net/sf/jsqlparser/"));
        assertThat(sessions.getInfos()).singleElement().satisfies(session -> assertThat(session.getId()).isEqualTo(agent.getSessionId()));
        agent.dump(false);
        if (System.getProperty("jacoco.destFile") != null) {
            Path evidence = exec.resolveSibling("fork-agents.txt");
            Files.writeString(evidence, String.join("\n", agents) + "\nsessionId=" + agent.getSessionId() + "\n");
        }
    }
}
