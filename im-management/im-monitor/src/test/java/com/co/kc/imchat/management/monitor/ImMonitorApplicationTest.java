package com.co.kc.imchat.management.monitor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = ImMonitorApplication.class, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "im.iam.enabled=false"
})
class ImMonitorApplicationTest {

    @Test
    void contextLoadsWithoutExternalDiscovery() {
    }
}
