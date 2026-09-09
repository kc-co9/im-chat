package com.co.kc.imchat.management.audit.sdk.support;

import com.co.kc.imchat.management.audit.sdk.annotation.Audited;
import com.co.kc.imchat.management.audit.sdk.annotation.AuditAttribute;
import com.co.kc.imchat.management.audit.sdk.client.AuditClient;
import com.co.kc.imchat.management.audit.sdk.context.AuditContextCollector;
import com.co.kc.imchat.management.audit.sdk.model.AuditContext;
import com.co.kc.imchat.management.audit.sdk.model.AuditActor;
import com.co.kc.imchat.management.audit.sdk.model.AuditAttributes;
import com.co.kc.imchat.management.audit.sdk.model.AuditClientContext;
import com.co.kc.imchat.management.audit.sdk.model.AuditEvent;
import com.co.kc.imchat.management.audit.sdk.model.AuditOutcome;
import com.co.kc.imchat.management.audit.sdk.model.AuditType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditedAspectTest {
    private final List<AuditEvent> events = new ArrayList<>();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void submitsSuccessfulCallImmediatelyWithoutTransaction() {
        Target target = proxy(events::add);

        String result = target.succeed(new Command(2001L));

        assertThat(result).isEqualTo("ok");
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo("USER_BAN");
            assertThat(event.target().type()).isEqualTo("USER");
            assertThat(event.target().id()).isEqualTo("2001");
            assertThat(event.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        });
    }

    @Test
    void resolvesTargetIdFromDeclaredParameterName() {
        Target target = proxy(events::add);

        target.succeedWithRequest(new Command(2002L));

        assertThat(events).singleElement()
                .extracting(event -> event.target().id())
                .isEqualTo("2002");
    }

    @Test
    void submitsSuccessfulCallOnlyAfterTransactionCommit() {
        Target target = proxy(events::add);
        TransactionSynchronizationManager.initSynchronization();

        target.succeed(new Command(2001L));
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();

        assertThat(events).isEmpty();
        assertThat(synchronizations).hasSize(1);
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        assertThat(events).singleElement()
                .extracting(AuditEvent::outcome)
                .isEqualTo(AuditOutcome.SUCCESS);
    }

    @Test
    void rollbackDoesNotSubmitSuccessfulEvent() {
        Target target = proxy(events::add);
        TransactionSynchronizationManager.initSynchronization();

        target.succeed(new Command(2001L));
        TransactionSynchronizationManager.clearSynchronization();

        assertThat(events).isEmpty();
    }

    @Test
    void submitsFailureAndPreservesOriginalException() {
        Target target = proxy(events::add);

        assertThatThrownBy(() -> target.fail(new Command(2001L)))
                .isSameAs(Target.FAILURE);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.outcome()).isEqualTo(AuditOutcome.FAILURE);
            assertThat(event.errorCode()).isEqualTo("INTERNAL_ERROR");
        });
    }

    @Test
    void transportFailureDoesNotChangeBusinessResult() {
        Target target = proxy(event -> {
            throw new IllegalStateException("transport unavailable");
        });

        String result = target.succeed(new Command(2001L));

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void recordsAnnotatedParameterAndSuccessfulResultAttributes() {
        Target target = proxy(events::add);

        Result result = target.succeedWithAttributes(
                new AttributeCommand(2001L, "hidden"),
                "not-recorded");

        assertThat(result).isEqualTo(new Result("ok", "hidden"));
        assertThat(events).singleElement()
                .extracting(AuditEvent::attributes)
                .isEqualTo(new AuditAttributes(Map.of(
                        "userId", "2001",
                        "status", "ok")));
    }

    @Test
    void recordsAnnotatedParameterAttributesWhenInvocationFails() {
        Target target = proxy(events::add);

        assertThatThrownBy(() -> target.failWithAttributes(
                new AttributeCommand(2001L, "hidden")))
                .isSameAs(Target.FAILURE);

        assertThat(events).singleElement()
                .extracting(AuditEvent::attributes)
                .isEqualTo(new AuditAttributes(Map.of("userId", "2001")));
    }

    @Test
    void recordsAnnotatedScalarParameterWithParameterName() {
        Target target = proxy(events::add);

        target.recordScalar("manual");

        assertThat(events).singleElement()
                .extracting(AuditEvent::attributes)
                .isEqualTo(new AuditAttributes(Map.of("reason", "manual")));
    }

    private Target proxy(AuditClient client) {
        AuditEventFactory factory = new AuditEventFactory(
                Clock.fixed(Instant.parse("2026-08-28T04:00:00Z"), ZoneOffset.UTC));
        AuditContextCollector collector = () -> new AuditContext(
                new AuditActor("ADMINISTRATOR", "1001", "admin"),
                new AuditClientContext("127.0.0.1", "JUnit"),
                "trace-1");
        AuditFailureReporter reporter = new AuditFailureReporter(new SimpleMeterRegistry());
        AuditedAspect aspect = new AuditedAspect(
                client,
                collector,
                factory,
                reporter);
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new Target());
        proxyFactory.addAspect(aspect);
        return proxyFactory.getProxy();
    }

    record Command(Long userId) {
    }

    record AttributeCommand(
            Long userId,
            @AuditAttribute(include = false) String password
    ) {
    }

    record Result(
            String status,
            @AuditAttribute(include = false) String internalValue
    ) {
    }

    static class Target {
        private static final IllegalStateException FAILURE =
                new IllegalStateException("expected");

        @Audited(
                type = AuditType.BUSINESS,
                action = "USER_BAN",
                targetType = "USER",
                targetId = "#command.userId()",
                description = "封禁普通用户")
        public String succeed(Command command) {
            return "ok";
        }

        @Audited(
                type = AuditType.BUSINESS,
                action = "USER_BAN",
                targetType = "USER",
                targetId = "#request.userId()",
                description = "封禁普通用户")
        public String succeedWithRequest(Command request) {
            return "ok";
        }

        @Audited(
                type = AuditType.BUSINESS,
                action = "USER_BAN",
                targetType = "USER",
                targetId = "#command.userId()",
                description = "封禁普通用户")
        public void fail(Command command) {
            throw FAILURE;
        }

        @Audited(
                type = AuditType.BUSINESS,
                action = "USER_BAN",
                targetType = "USER",
                targetId = "#command.userId()",
                description = "封禁普通用户")
        public Result succeedWithAttributes(
                @AuditAttribute AttributeCommand command,
                String ignored
        ) {
            return new Result("ok", "hidden");
        }

        @Audited(
                type = AuditType.BUSINESS,
                action = "USER_BAN",
                targetType = "USER",
                targetId = "#command.userId()",
                description = "封禁普通用户")
        public void failWithAttributes(@AuditAttribute AttributeCommand command) {
            throw FAILURE;
        }

        @Audited(
                type = AuditType.BUSINESS,
                action = "USER_BAN",
                targetType = "USER",
                description = "封禁普通用户")
        public void recordScalar(@AuditAttribute String reason) {
        }
    }
}
