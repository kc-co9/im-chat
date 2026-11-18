package com.co.kc.imchat.management.ops;

import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Tag("startup-smoke")
@SpringBootTest(classes = ImOpsApplication.class, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "im.audit.enabled=false",
        "im.iam.application.clients.catalog.client-id=",
        "im.iam.application.clients.catalog.client-secret="
})
class ImOpsApplicationTest {
    @MockitoBean
    private IamIntrospectionClient introspectionClient;
    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @Test
    void contextLoadsWithoutExternalDiscovery() {
    }
}
