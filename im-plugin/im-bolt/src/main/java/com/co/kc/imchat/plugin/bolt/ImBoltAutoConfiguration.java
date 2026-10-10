package com.co.kc.imchat.plugin.bolt;

import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.RpcServer;
import com.alipay.remoting.config.BoltClientOption;
import com.alipay.remoting.config.BoltServerOption;
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
    public RpcClient boltRpcClient(ImBoltProperties properties) {
        RpcClient client = new RpcClient();
        ImBoltProperties.Tls tls = properties.getClient().getTls();
        client.option(BoltClientOption.CLI_SSL_ENABLE, tls.isEnabled());
        if (tls.isEnabled()) {
            requireKeyStore(tls);
            client.option(BoltClientOption.CLI_SSL_KEYSTORE, tls.getKeyStore());
            client.option(BoltClientOption.CLI_SSL_KEYSTORE_PASS, tls.getKeyStorePassword());
            client.option(BoltClientOption.CLI_SSL_KEYSTORE_TYPE, tls.getKeyStoreType());
        }
        return client;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "im.bolt.server", name = "enabled", havingValue = "true")
    public RpcServer boltRpcServer(ImBoltProperties properties) {
        RpcServer server = new RpcServer(
                properties.getServer().getHost(), properties.getServer().getPort());
        ImBoltProperties.Tls tls = properties.getServer().getTls();
        server.option(BoltServerOption.SRV_SSL_ENABLE, tls.isEnabled());
        server.option(BoltServerOption.SRV_SSL_NEED_CLIENT_AUTH, tls.isNeedClientAuth());
        if (tls.isEnabled()) {
            requireKeyStore(tls);
            server.option(BoltServerOption.SRV_SSL_KEYSTORE, tls.getKeyStore());
            server.option(BoltServerOption.SRV_SSL_KEYSTORE_PASS, tls.getKeyStorePassword());
            server.option(BoltServerOption.SRV_SSL_KEYSTORE_TYPE, tls.getKeyStoreType());
        }
        return server;
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

    private void requireKeyStore(ImBoltProperties.Tls tls) {
        if (tls.getKeyStore() == null || tls.getKeyStore().isBlank()
                || tls.getKeyStorePassword() == null) {
            throw new IllegalArgumentException(
                    "Bolt TLS key-store and key-store-password are required when TLS is enabled");
        }
    }
}
