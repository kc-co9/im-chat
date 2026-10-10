package com.co.kc.imchat.management.ops;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("startup-smoke")
@SpringBootTest(classes = ImOpsApplication.class, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "im.iam.enabled=false"
})
class ImOpsApplicationTest {

    @Test
    void contextLoadsWithoutExternalDiscovery() {
    }
}
