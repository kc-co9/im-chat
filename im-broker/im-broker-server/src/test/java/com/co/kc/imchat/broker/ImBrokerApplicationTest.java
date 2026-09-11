package com.co.kc.imchat.broker;

import com.co.kc.imchat.broker.domain.registry.connection.ConnectionRegistry;
import com.co.kc.imchat.broker.interfaces.handler.frame.FrameProcessHandler;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.service.message.facade.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("startup-smoke")
@SpringBootTest(classes = ImBrokerApplication.class, properties = {
        "im.bolt.client.enabled=false",
        "im.bolt.server.enabled=false",
        "im.dubbo.enabled=false",
        "dubbo.enabled=false",
        "jetcache.remote.default.type=mock",
        "im.dubbo.registry.address=N/A",
        "dubbo.registry.address=N/A",
        "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "server.port=0"
})
class ImBrokerApplicationTest {

    @Autowired
    private FrameProcessHandler frameProcessHandler;

    @Autowired
    private ConnectionRegistry connectionRegistry;

    @MockitoBean
    private BoltInvoker boltInvoker;

    @MockitoBean
    private MessageService messageService;

    @Test
    void contextLoadsWithTestLocalProtocols() {
        assertThat(frameProcessHandler).isNotNull();
        assertThat(connectionRegistry).isNotNull();
    }

}
