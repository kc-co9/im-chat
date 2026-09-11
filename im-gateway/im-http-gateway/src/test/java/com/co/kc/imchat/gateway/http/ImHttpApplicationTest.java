package com.co.kc.imchat.gateway.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("startup-smoke")
@SpringBootTest(classes = ImHttpApplication.class, properties = {
        "server.port=0",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "im.dubbo.enabled=false",
        "dubbo.enabled=false"
})
class ImHttpApplicationTest {

    @Test
    void contextLoads() {
    }
}
