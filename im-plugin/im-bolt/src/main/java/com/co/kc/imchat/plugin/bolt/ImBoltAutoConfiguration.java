package com.co.kc.imchat.plugin.bolt;

import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.RpcServer;
import com.co.kc.imchat.plugin.bolt.core.BoltClientInvoker;
import com.co.kc.imchat.plugin.bolt.core.BoltRpcClient;
import com.co.kc.imchat.plugin.bolt.core.BoltRequestProcessor;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.bolt.spi.BoltRequestHandler;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
@ConditionalOnClass(RpcClient.class)
@EnableConfigurationProperties(ImBoltProperties.class)
public class ImBoltAutoConfiguration {

    @Bean(initMethod = "init", destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "im.bolt.client", name = "enabled", havingValue = "true")
    public RpcClient boltRpcClient() {
        return new RpcClient();
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "im.bolt.server", name = "enabled", havingValue = "true")
    public RpcServer boltRpcServer(ImBoltProperties properties) {
        return new RpcServer(properties.getServer().getPort());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RpcClient.class)
    public BoltInvoker boltInvoker(RpcClient rpcClient) {
        return new BoltClientInvoker(rpcClient);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(BoltInvoker.class)
    public BoltRpcClient boltRemoteClient(BoltInvoker boltInvoker) {
        return new BoltRpcClient(boltInvoker);
    }

    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean
    public BoltRequestProcessor boltInboundProcessor(List<BoltRequestHandler> handlers) {
        return new BoltRequestProcessor(handlers);
    }

    @Bean
    @ConditionalOnBean(RpcServer.class)
    public ApplicationRunner boltInboundProcessorRegistrar(RpcServer rpcServer,
                                                           BoltRequestProcessor processor) {
        return args -> rpcServer.registerUserProcessor(processor);
    }
}
