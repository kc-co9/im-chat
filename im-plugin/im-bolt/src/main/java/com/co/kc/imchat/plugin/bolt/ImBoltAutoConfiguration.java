package com.co.kc.imchat.plugin.bolt;

import com.co.kc.imchat.plugin.bolt.model.enums.BoltTransportMode;
import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.RpcServer;
import com.co.kc.imchat.plugin.bolt.invocation.BoltClientInvoker;
import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.co.kc.imchat.plugin.bolt.client.BoltRpcClient;
import com.co.kc.imchat.plugin.bolt.server.BoltRequestProcessor;
import com.co.kc.imchat.plugin.bolt.security.BoltDevelopmentValidator;
import com.co.kc.imchat.plugin.bolt.server.BoltControlledServer;
import org.springframework.core.env.Environment;
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
import org.springframework.beans.factory.SmartInitializingSingleton;

@AutoConfiguration
@ConditionalOnClass(RpcClient.class)
@EnableConfigurationProperties(ImBoltProperties.class)
public class ImBoltAutoConfiguration {

    @Bean(initMethod = "startup", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RpcClient.class)
    @ConditionalOnProperty(prefix = "im.bolt.client", name = "enabled", havingValue = "true")
    public RpcClient boltRpcClient(ImBoltProperties properties, Environment environment) {
        validateModes(properties, environment);
        return new BoltControlledClient(properties.getClient());
    }

    @Bean
    @ConditionalOnProperty(prefix = "im.bolt.client", name = "enabled", havingValue = "true")
    public SmartInitializingSingleton boltControlledAssemblyValidator(
            RpcClient client, BoltInvoker invoker, ImBoltProperties properties, Environment environment) {
        return () -> {
            validateModes(properties, environment);
            if (!(client instanceof BoltControlledClient controlled)
                    || !(invoker instanceof BoltClientInvoker owned) || !owned.usesControlledClient(controlled)) {
                throw new IllegalArgumentException("Bolt enabled client requires owned controlled client and invoker assembly");
            }
            if (controlled.configuredMode() != properties.getClient().getMode()) {
                throw new IllegalArgumentException("Bolt actual client mode does not match configured process mode");
            }
            if (controlled.configuredMode() == BoltTransportMode.DEVELOPMENT_PLAINTEXT) {
                BoltDevelopmentValidator.validateProfiles(properties.getClient().getDevelopment(), List.of(environment.getActiveProfiles()));
            }
            if (!controlled.isRunningControlledAssembly()) {
                throw new IllegalArgumentException("Bolt enabled client requires running controlled assembly");
            }
        };
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "im.bolt.server", name = "enabled", havingValue = "true")
    public RpcServer boltRpcServer(ImBoltProperties properties, Environment environment) {
        validateModes(properties, environment);
        return new BoltControlledServer(properties.getServer());
    }

    @Bean
    @ConditionalOnProperty(prefix = "im.bolt.server", name = "enabled", havingValue = "true")
    public SmartInitializingSingleton boltControlledServerValidator(RpcServer server, ImBoltProperties properties, Environment environment) {
        return () -> {
            validateModes(properties, environment);
            if (!(server instanceof BoltControlledServer controlled)) {
                throw new IllegalArgumentException("Bolt enabled server requires fixed controlled server assembly");
            }
            ImBoltProperties.Server configured = properties.getServer();
            if (controlled.configuredMode() != configured.getMode() || !server.ip().equals(configured.getHost())
                    || (configured.getPort() != 0 && server.port() != configured.getPort())) {
                throw new IllegalArgumentException("Bolt actual server mode or binding does not match process configuration");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RpcClient.class)
    public BoltInvoker boltInvoker(RpcClient rpcClient, ImBoltProperties properties) {
        return new BoltClientInvoker(rpcClient, System::nanoTime, properties.getClient());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(BoltInvoker.class)
    public BoltRpcClient boltRemoteClient(BoltInvoker boltInvoker, ImBoltProperties properties) {
        return new BoltRpcClient(boltInvoker, properties.getClient());
    }

    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean
    public BoltRequestProcessor boltInboundProcessor(List<BoltRequestHandler> handlers, ImBoltProperties properties) {
        return new BoltRequestProcessor(handlers, properties.getServer());
    }

    @Bean
    @ConditionalOnBean(RpcServer.class)
    public ApplicationRunner boltInboundProcessorRegistrar(RpcServer rpcServer,
                                                           BoltRequestProcessor processor) {
        return args -> rpcServer.registerUserProcessor(processor);
    }

    private void validateModes(ImBoltProperties properties, Environment environment) {
        ImBoltProperties.Client client = properties.getClient();
        ImBoltProperties.Server server = properties.getServer();
        if (client.isEnabled() && server.isEnabled() && client.getMode() != server.getMode()) {
            throw new IllegalArgumentException("Bolt client and server modes must match");
        }
        if (client.isEnabled() && client.getMode() == BoltTransportMode.DEVELOPMENT_PLAINTEXT) {
            BoltDevelopmentValidator.validateProfiles(client.getDevelopment(), List.of(environment.getActiveProfiles()));
            client.validateTypedPolicy();
        }
        if (server.isEnabled() && server.getMode() == BoltTransportMode.DEVELOPMENT_PLAINTEXT) {
            BoltDevelopmentValidator.validateProfiles(server.getDevelopment(), List.of(environment.getActiveProfiles()));
            BoltDevelopmentValidator.validateAddress(server.getHost(), server.getDevelopment());
            if (server.getTls().isEnabled()) { throw new IllegalArgumentException("Bolt development server cannot enable TLS"); }
        }
    }

}
