package com.co.kc.imchat.management.audit.sdk.properties;

import com.co.kc.imchat.management.audit.sdk.transport.AuditTransportType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditPropertiesTest {

    @Test
    void disabledConfigurationKeepsTransportConfiguration() {
        AuditProperties properties = new AuditProperties(
                false,
                AuditTransportType.KAFKA,
                kafka(),
                http(),
                iam());

        assertThat(properties.enabled()).isFalse();
    }

    @Test
    void transportIsAlwaysRequired() {
        assertThatThrownBy(() -> new AuditProperties(
                false,
                null,
                kafka(),
                http(),
                iam()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transport");
    }

    @Test
    void enabledHttpConfigurationRequiresRemoteIdentity() {
        assertThatThrownBy(() -> new AuditProperties(
                true,
                AuditTransportType.HTTP,
                kafka(),
                http(),
                iam()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    void enabledHttpConfigurationRequiresIamIdentity() {
        assertThatThrownBy(() -> new AuditProperties(
                true,
                AuditTransportType.HTTP,
                kafka(),
                new AuditHttpProperties(
                        URI.create("http://audit.internal/internal/audits"),
                        Duration.ofSeconds(3), 2, 1000, 3, Duration.ofMillis(200)),
                emptyIam()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token URI");
    }

    @Test
    void enabledKafkaConfigurationDoesNotRequireIamIdentity() {
        AuditProperties properties = new AuditProperties(
                true,
                AuditTransportType.KAFKA,
                kafka(),
                http(),
                emptyIam());

        assertThat(properties.enabled()).isTrue();
    }

    @Test
    void masksIamClientSecretInDiagnosticText() {
        AuditIamProperties properties = new AuditIamProperties(
                URI.create("http://iam.internal/oauth2/token"),
                "im-admin-audit",
                "never-print-this-secret",
                Duration.ofSeconds(3),
                Duration.ofSeconds(30));

        assertThat(properties.toString())
                .doesNotContain("never-print-this-secret")
                .contains("clientSecret=***");
    }

    private AuditKafkaProperties kafka() {
        return new AuditKafkaProperties("auditOutput");
    }

    private AuditHttpProperties http() {
        return new AuditHttpProperties(
                null,
                Duration.ofSeconds(3),
                2,
                1000,
                3,
                Duration.ofMillis(200));
    }

    private AuditIamProperties iam() {
        return new AuditIamProperties(
                URI.create("http://iam.internal/oauth2/token"),
                "im-admin-audit",
                "client-secret",
                Duration.ofSeconds(3),
                Duration.ofSeconds(30));
    }

    private AuditIamProperties emptyIam() {
        return new AuditIamProperties(
                null,
                null,
                null,
                Duration.ofSeconds(3),
                Duration.ofSeconds(30));
    }
}
