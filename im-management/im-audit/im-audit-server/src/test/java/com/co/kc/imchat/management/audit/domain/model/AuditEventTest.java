package com.co.kc.imchat.management.audit.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventTest {

    @Test
    void builderDoesNotExposePersistencePrimaryKey() {
        Object builder = AuditEvent.builder();

        assertThat(builder.getClass().getSimpleName()).isEqualTo("Builder");
        assertThat(Arrays.stream(builder.getClass().getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .doesNotContain("pkId");
    }

    @Test
    void builderPopulatesAggregateWithoutLongArgumentConstructor() {
        assertThat(AuditEvent.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> {
                    assertThat(constructor.getParameterCount()).isZero();
                    assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
                });
    }

    @Test
    void buildsAnImmutableCompletedAuditFact() {
        Map<String, String> attributes = new LinkedHashMap<>(Map.of("role", "operator"));

        AuditEvent event = AuditEvent.builder()
                .id(new AuditId("audit-1"))
                .type(AuditType.BUSINESS)
                .sourceApp(new SourceApp("imAdmin"))
                .action(new AuditAction("USER_BAN"))
                .actor(new AuditActor("ADMINISTRATOR", "1001", "admin"))
                .target(new AuditTarget("USER", "2001"))
                .outcome(AuditOutcome.SUCCESS)
                .description(new AuditDescription("封禁普通用户"))
                .clientContext(new AuditClientContext("127.0.0.1", "JUnit"))
                .traceId(new TraceId("trace-1"))
                .attributes(new AuditAttributes(attributes))
                .occurredAt(Instant.parse("2026-08-28T04:00:00Z"))
                .build();
        attributes.put("password", "must-not-appear");

        assertThat(event.getId()).isEqualTo(new AuditId("audit-1"));
        assertThat(event.getAttributes().values()).containsOnlyKeys("role");
        assertThat(AuditEvent.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().startsWith("set")
                        || method.getName().equals("update")
                        || method.getName().equals("delete"));
    }

    @Test
    void failedFactRequiresStableErrorCode() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> AuditEvent.builder()
                        .id(new AuditId("audit-2"))
                        .type(AuditType.SECURITY)
                        .sourceApp(new SourceApp("imIam"))
                        .action(new AuditAction("LOGIN_FAILURE"))
                        .actor(new AuditActor("ADMINISTRATOR", null, "unknown"))
                        .target(new AuditTarget("SESSION", null))
                        .outcome(AuditOutcome.FAILURE)
                        .description(new AuditDescription("登录失败"))
                        .attributes(new AuditAttributes(Map.of()))
                        .occurredAt(Instant.parse("2026-08-28T04:00:00Z"))
                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("error code");
    }
}
