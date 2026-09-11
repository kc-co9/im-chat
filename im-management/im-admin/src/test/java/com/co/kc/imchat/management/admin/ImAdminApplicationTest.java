package com.co.kc.imchat.management.admin;

import com.co.kc.imchat.management.admin.adapter.AccountAdminAdapter;
import com.co.kc.imchat.management.iam.sdk.security.IamCsrfTokenRepository;
import com.co.kc.imchat.management.iam.sdk.security.IamSecurityExceptionHandler;
import com.co.kc.imchat.management.iam.sdk.security.IamSecurityFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

@Tag("startup-smoke")
@SpringBootTest(classes = ImAdminApplication.class, properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration,"
                + "org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                + "org.apache.dubbo.spring.boot.autoconfigure.DubboRelaxedBinding2AutoConfiguration",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "im.iam.enabled=false",
        "im.dubbo.enabled=false",
        "dubbo.enabled=false",
        "dubbo.application.qos-enable=false",
        "dubbo.protocol.name=injvm",
        "dubbo.registry.address=N/A"
})
class ImAdminApplicationTest {

    @MockitoBean
    private AccountAdminAdapter accountAdminAdapter;

    @MockitoBean
    private IamSecurityFilter iamSecurityFilter;

    @MockitoBean
    private IamCsrfTokenRepository csrfTokenRepository;

    @MockitoBean
    private IamSecurityExceptionHandler securityExceptionHandler;

    @Test
    void contextLoadsWithoutExternalInfrastructure(ApplicationContext context) {
        assertThat(context.getBeansOfType(RedissonClient.class)).isEmpty();
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(context.getEnvironment().getProperty("im.dubbo.registry.namespace"))
                .isEqualTo("b0b51fe2-fd46-463e-96df-dfba4a3b41a1");
    }
}
