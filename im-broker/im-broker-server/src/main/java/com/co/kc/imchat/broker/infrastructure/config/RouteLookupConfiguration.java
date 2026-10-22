package com.co.kc.imchat.broker.infrastructure.config;

import com.co.kc.imchat.broker.domain.connection.repository.ConnectionRegistry;
import com.co.kc.imchat.broker.domain.connection.repository.GatewayRegistry;
import com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter;
import com.co.kc.imchat.broker.infrastructure.config.properties.RegistryProperties;
import com.co.kc.imchat.broker.interfaces.handler.routing.RouteLookupHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/** 有界 RouteLookup 和 Broker 数据面授权装配。 */
@Configuration
public class RouteLookupConfiguration {

    /** 阻塞式 peer 查询使用专用虚拟线程执行器，由配置层负责关闭。 */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService brokerRouteLookupExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public RouteLookupHandler routeLookupHandler(
            ConnectionRegistry connectionRegistry,
            GatewayRegistry gatewayRegistry,
            BrokerRaftAdapter repository,
            RegistryProperties registryProperties) {
        return new RouteLookupHandler(connectionRegistry, gatewayRegistry,
                repository, registryProperties.getGatewayTtl(),
                Clock.systemUTC());
    }
}
