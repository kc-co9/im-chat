package com.co.kc.imchat.management.audit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("startup-smoke")
class ImAuditApplicationTest {

    // startup-smoke isolation contract: a future context test must keep these external integrations disabled.
    private static final String NACOS_DISCOVERY_DISABLED = "spring.cloud.nacos.discovery.enabled=false";
    private static final String NACOS_CONFIG_DISABLED = "spring.cloud.nacos.config.enabled=false";

    @Test
    void declaresSpringBootApplicationEntryPoint() {
        assertThat(ImAuditApplication.class).hasAnnotation(SpringBootApplication.class);
    }
}
