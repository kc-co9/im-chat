package com.co.kc.imchat.management.audit.sdk;

import com.co.kc.imchat.management.audit.sdk.client.AuditClient;
import com.co.kc.imchat.management.audit.sdk.context.AuditContextCollector;
import com.co.kc.imchat.management.audit.sdk.support.AuditedAspect;
import com.co.kc.imchat.management.audit.sdk.transport.AuditTransport;
import com.co.kc.imchat.management.audit.sdk.transport.http.HttpAuditTransport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ImAuditSdkAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ImAuditSdkAutoConfiguration.class));

    @Test
    void remainsDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(AuditedAspect.class);
            assertThat(context).doesNotHaveBean(AuditContextCollector.class);
        });
    }

    @Test
    void createsAuditingCapabilityWhenEnabled() {
        contextRunner
                .withBean(AuditClient.class, () -> event -> {
                })
                .withPropertyValues(
                        "im.audit.enabled=true",
                        "im.audit.transport=kafka")
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditedAspect.class);
                    assertThat(context).hasSingleBean(AuditContextCollector.class);
                });
    }

    @Test
    void keepsApplicationContextCollectorOverride() {
        AuditContextCollector collector = () -> null;

        contextRunner
                .withBean(AuditClient.class, () -> event -> {
                })
                .withBean(AuditContextCollector.class, () -> collector)
                .withPropertyValues(
                        "im.audit.enabled=true",
                        "im.audit.transport=kafka")
                .run(context -> assertThat(context.getBean(AuditContextCollector.class))
                        .isSameAs(collector));
    }

    @Test
    void failsStartupWithoutAuditClient() {
        contextRunner
                .withPropertyValues(
                        "im.audit.enabled=true",
                        "im.audit.transport=kafka")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void createsHttpTransportWithoutKafkaBinder() {
        contextRunner
                .withPropertyValues(
                        "im.audit.enabled=true",
                        "im.audit.transport=http",
                        "im.audit.http.endpoint=http://audit.internal/internal/audits",
                        "im.audit.iam.token-uri=http://iam.internal/oauth2/token",
                        "im.audit.iam.client-id=im-admin-audit",
                        "im.audit.iam.client-secret=client-secret")
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditClient.class);
                    assertThat(context).hasSingleBean(AuditTransport.class);
                    assertThat(context).hasSingleBean(HttpAuditTransport.class);
                });
    }

    @Test
    void failsStartupWhenHttpIdentityIsIncomplete() {
        contextRunner
                .withPropertyValues(
                        "im.audit.enabled=true",
                        "im.audit.transport=http")
                .run(context -> assertThat(context).hasFailed());
    }

}
