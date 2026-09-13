package com.co.kc.imchat.plugin.tracing;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImTracingLogbackTest {

    @Test
    void exposesCodeLevelTraceIdAccessThroughSkyWalkingToolkit() {
        assertThat(TracingUtils.currentTraceId()).isEqualTo("N/A");
    }

    @Test
    void providesSkyWalkingLogbackLayoutAndCommonPattern() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        assertThat(ClassUtils.isPresent(
                "org.apache.skywalking.apm.toolkit.log.logback.v1.x.LogbackPatternConverter",
                classLoader)).isTrue();
        assertThat(ClassUtils.isPresent(
                "org.apache.skywalking.apm.toolkit.log.logback.v1.x.TraceIdPatternLogbackLayout",
                classLoader)).isTrue();

        ClassPathResource resource = new ClassPathResource(
                "com/co/kc/imchat/plugin/tracing/logback-common.xml");
        assertThat(resource.exists()).isTrue();
        String xml = resource.getContentAsString(StandardCharsets.UTF_8);
        assertThat(xml)
                .contains("conversionWord=\"tid\"")
                .contains("LogbackPatternConverter")
                .contains("TraceIdPatternLogbackLayout")
                .contains("[%tid] [%X{traceId}]")
                .contains("name=\"IM_CONSOLE\"");
    }

    @Test
    void loadsConverterAndLayoutWithAnOverridablePattern() throws Exception {
        PrintStream originalOut = System.out;
        String originalPattern = System.getProperty("IM_LOG_PATTERN");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());

        try (PrintStream capturedOut = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            System.setProperty("IM_LOG_PATTERN", "OVERRIDE [%tid] [%X{traceId}] %msg%n");
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(new ClassPathResource("logback-test.xml").getURL());
            context.start();
            context.getMDCAdapter().put("traceId", "mdc-trace");

            Logger logger = context.getLogger(Logger.ROOT_LOGGER_NAME);
            logger.info("trace-message");

            assertThat(new StatusUtil(context).getHighestLevel(0)).isLessThan(Status.WARN);
            assertThat(output.toString(StandardCharsets.UTF_8))
                    .contains("OVERRIDE [TID: N/A] [mdc-trace] trace-message")
                    .contains("STANDARD [TID: N/A] trace-message");
        } finally {
            context.stop();
            System.setOut(originalOut);
            if (originalPattern == null) {
                System.clearProperty("IM_LOG_PATTERN");
            } else {
                System.setProperty("IM_LOG_PATTERN", originalPattern);
            }
        }
    }
}
