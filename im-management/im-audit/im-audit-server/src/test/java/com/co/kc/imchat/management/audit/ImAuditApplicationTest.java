package com.co.kc.imchat.management.audit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

class ImAuditApplicationTest {

    @Test
    void declaresSpringBootApplicationEntryPoint() {
        assertThat(ImAuditApplication.class)
                .hasAnnotation(SpringBootApplication.class);
    }
}
