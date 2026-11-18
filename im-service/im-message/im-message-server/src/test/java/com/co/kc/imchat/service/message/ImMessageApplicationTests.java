package com.co.kc.imchat.service.message;

import com.co.kc.imchat.plugin.bolt.model.enums.BoltTransportMode;
import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.plugin.bolt.client.BoltControlledClient;
import com.co.kc.imchat.plugin.bolt.properties.ImBoltProperties;
import com.co.kc.imchat.service.message.adapter.social.SocialAdapter;
import com.co.kc.imchat.service.message.infrastructure.mybatis.mapper.DbImGroupChatMapper;
import com.co.kc.imchat.service.message.infrastructure.mybatis.mapper.DbImGroupInboxMessageMapper;
import com.co.kc.imchat.service.message.infrastructure.mybatis.mapper.DbImPrivateChatMapper;
import com.co.kc.imchat.service.message.infrastructure.mybatis.mapper.DbImPrivateInboxMessageMapper;
import com.co.kc.imchat.service.message.infrastructure.notification.redis.RedisImMessageConfirmableStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 正式 Message/Broker SDK/受控 Bolt 装配；数据库、Redis及Social远端替换，不代表全栈可用。
 */
@Tag("startup-smoke")
@org.springframework.context.annotation.Import(ImMessageApplicationTests.DiscoveryFixture.class)
@ActiveProfiles("bolt-dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = ImMessageApplication.class, properties = {
        "IM_BOLT_SYNTHETIC_DATA=true", "server.port=0", "jetcache.remote.default.type=mock",
        "im.message.broker.bolt.bootstrap-targets[0].address=127.0.0.1:18020",
        "im.message.broker.bolt.bootstrap-targets[0].expected-peer-identity=spiffe://im-chat/broker/broker-local-1",
        "im.datasource.sharding.enabled=false", "im.message.delay.worker.enabled=false",
        "im.dubbo.enabled=false", "dubbo.enabled=false", "im.dubbo.registry.address=N/A",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClientAutoConfiguration,"
                + "org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration",
        "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
public class ImMessageApplicationTests {
    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;
    @MockitoBean
    private RedissonClient redissonClient;
    @MockitoBean
    private RedisImMessageConfirmableStore confirmableStore;
    @org.springframework.test.context.bean.override.convention.TestBean(methodName = "externalSocialAdapter")
    private SocialAdapter socialAdapter;

    static SocialAdapter externalSocialAdapter() {
        return new SocialAdapter(org.mockito.Mockito.mock(com.co.kc.imchat.service.social.facade.SocialService.class));
    }
    @MockitoBean
    private org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory;
    @MockitoBean
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockitoBean
    private DbImGroupChatMapper groupChatMapper;
    @MockitoBean
    private DbImGroupInboxMessageMapper groupInboxMapper;
    @MockitoBean
    private DbImPrivateChatMapper privateChatMapper;
    @MockitoBean
    private DbImPrivateInboxMessageMapper privateInboxMapper;
    @Autowired
    private BrokerClient brokerClient;
    @Autowired
    private BoltControlledClient client;

    @Test
    void contextLoads() {
        assertThat(brokerClient).isNotNull();
        assertThat(client.isRunningControlledAssembly()).isTrue();
        assertThat(client.configuredMode()).isEqualTo(BoltTransportMode.DEVELOPMENT_PLAINTEXT);
        org.mockito.Mockito.verifyNoInteractions(sqlSessionFactory, transactionManager);
    }

    /**
     * 外部发现候选在 Bean 返回之前准备，与正式 YAML 的 bootstrap 配对求交；不伪造 Broker RPC 成功。
     */
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class DiscoveryFixture {
        @org.springframework.context.annotation.Bean
        com.co.kc.imchat.plugin.lock.core.DistributedLockTemplate startupDistributedLockTemplate(RedissonClient redis) {
            return new com.co.kc.imchat.plugin.lock.core.DistributedLockTemplate(
                    new com.co.kc.imchat.plugin.lock.core.RedissonLockClient(redis));
        }

        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        org.springframework.cloud.client.discovery.DiscoveryClient startupDiscoveryClient() {
            org.springframework.cloud.client.discovery.DiscoveryClient discovery = org.mockito.Mockito.mock(
                    org.springframework.cloud.client.discovery.DiscoveryClient.class);
            org.mockito.Mockito.when(discovery.getInstances(com.co.kc.imchat.common.model.enums.ServiceName.IM_BROKER.value()))
                    .thenReturn(java.util.List.of(new org.springframework.cloud.client.DefaultServiceInstance(
                            "broker-local-1", com.co.kc.imchat.common.model.enums.ServiceName.IM_BROKER.value(),
                            "127.0.0.1", 18020, false)));
            return discovery;
        }
    }
}
