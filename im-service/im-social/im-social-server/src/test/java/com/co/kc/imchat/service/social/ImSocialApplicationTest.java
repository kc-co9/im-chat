package com.co.kc.imchat.service.social;

import com.co.kc.imchat.plugin.web.advice.ResultAdvice;
import com.co.kc.imchat.plugin.datasource.transaction.AfterTransactionCommitTemplate;
import com.co.kc.imchat.service.social.adapter.account.AccountAdapter;
import com.co.kc.imchat.service.social.adapter.message.MessageSocialAdapter;
import com.co.kc.imchat.service.social.infrastructure.mybatis.mapper.DbFriendMapper;
import com.co.kc.imchat.service.social.infrastructure.mybatis.mapper.DbImGroupMapper;
import com.co.kc.imchat.service.social.infrastructure.mybatis.mapper.DbImGroupMemberMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "server.port=0",
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
class ImSocialApplicationTest {
    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private DbFriendMapper friendMapper;

    @MockitoBean
    private DbImGroupMapper groupMapper;

    @MockitoBean
    private DbImGroupMemberMapper groupMemberMapper;

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private AccountAdapter accountAdapter;

    @MockitoBean
    private MessageSocialAdapter messageSocialAdapter;

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
