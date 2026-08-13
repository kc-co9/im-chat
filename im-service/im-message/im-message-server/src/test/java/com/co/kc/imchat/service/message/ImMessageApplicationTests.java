package com.co.kc.imchat.service.message;

import com.co.kc.imchat.service.message.support.ImChatSpringBootTest;
import com.co.kc.imchat.service.message.support.TestCacheConfiguration;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ImChatSpringBootTest
@Import(TestCacheConfiguration.class)
public class ImMessageApplicationTests {

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private RedissonClient redissonClient;

    @Test
    void contextLoads() {
    }
}
