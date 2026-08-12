package com.co.kc.imchat.broker;

import com.co.kc.imchat.broker.domain.registry.connection.ConnectionRegistry;
import com.co.kc.imchat.broker.interfaces.handler.frame.FrameProcessHandler;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.service.message.facade.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest(properties = {
        "im.bolt.client.enabled=false",
        "im.bolt.server.enabled=false",
        "im.dubbo.registry.address=N/A",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "im.broker.message-service.enabled=false"
})
@Import(ImBrokerApplicationTest.MockMessageServiceConfig.class)
class ImBrokerApplicationTest {

    @Autowired
    private FrameProcessHandler frameProcessHandler;

    @Autowired
    private ConnectionRegistry connectionRegistry;

    @MockitoBean
    private BoltInvoker boltInvoker;

    @Test
    void contextLoadsWithTestLocalProtocols() {
        assertThat(frameProcessHandler).isNotNull();
        assertThat(connectionRegistry).isNotNull();
    }

    @TestConfiguration
    static class MockMessageServiceConfig {

        @Bean
        MessageService messageService() {
            return mock(MessageService.class);
        }
    }
}
