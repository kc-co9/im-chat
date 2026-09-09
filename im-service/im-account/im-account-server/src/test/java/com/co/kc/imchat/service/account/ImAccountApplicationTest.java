package com.co.kc.imchat.service.account;

import com.co.kc.imchat.broker.sdk.BrokerClient;
import com.co.kc.imchat.plugin.web.advice.ResultAdvice;
import com.co.kc.imchat.plugin.bolt.spi.BoltInvoker;
import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommitTemplate;
import com.co.kc.imchat.plugin.lock.core.DistributedLockTemplate;
import com.co.kc.imchat.plugin.session.token.codec.JwtTokenCodec;
import com.co.kc.imchat.service.account.infrastructure.mybatis.mapper.DbUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "server.port=0",
        "im.session.jwt.enabled=false",
        "im.dubbo.enabled=false",
        "dubbo.enabled=false",
        "jetcache.remote.default.type=mock",
        "im.dubbo.registry.address=N/A",
        "spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class ImAccountApplicationTest {
    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private DbUserMapper dbUserMapper;

    @MockitoBean
    private JwtTokenCodec jwtTokenCodec;

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private BoltInvoker boltInvoker;

    @MockitoBean
    private DiscoveryClient discoveryClient;

    @MockitoBean
    private BrokerClient brokerClient;

    @MockitoBean
    private DistributedLockTemplate distributedLockTemplate;

    @MockitoBean
    private AfterTransactionCommitTemplate afterTransactionCommitTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void loadsHttpResultAdviceFromImWebAutoConfiguration() {
        applicationContext.getBean(ResultAdvice.class);
    }
}
