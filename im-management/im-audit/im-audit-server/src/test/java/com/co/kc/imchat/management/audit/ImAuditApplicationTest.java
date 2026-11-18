package com.co.kc.imchat.management.audit;

import com.co.kc.imchat.management.audit.application.AuditExportAppService;
import com.co.kc.imchat.management.audit.application.AuditIngestionAppService;
import com.co.kc.imchat.management.audit.application.AuditQueryAppService;
import com.co.kc.imchat.management.audit.domain.repository.AuditEventRepository;
import com.co.kc.imchat.management.audit.infrastructure.mybatis.service.DbAuditEventService;
import com.co.kc.imchat.management.audit.interfaces.http.AuditExportController;
import com.co.kc.imchat.management.audit.interfaces.http.AuditIngestionController;
import com.co.kc.imchat.management.audit.interfaces.http.AuditQueryController;
import com.co.kc.imchat.management.iam.sdk.properties.IamProperties;
import com.co.kc.imchat.management.iam.sdk.security.IamCsrfTokenRepository;
import com.co.kc.imchat.management.iam.sdk.security.IamSecurityExceptionHandler;
import com.co.kc.imchat.management.iam.sdk.security.IamSecurityFilter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.stream.binding.BindingService;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;

@Tag("startup-smoke")
@SpringBootTest(classes = ImAuditApplication.class, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "im.iam.enabled=false",
        "im.audit.enabled=false",
        "im.datasource.sharding.enabled=false",
        "spring.cloud.function.definition=",
        "spring.cloud.stream.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@Import(ImAuditApplicationTest.SecurityProperties.class)
class ImAuditApplicationTest {

    @Autowired
    private ConfigurableApplicationContext context;

    // Replace only persistence and external IAM collaborators; application wiring stays real.
    @MockitoBean
    private AuditEventRepository auditEventRepository;

    @MockitoBean
    private DbAuditEventService dbAuditEventService;

    @MockitoBean
    private IamSecurityFilter iamSecurityFilter;

    @MockitoBean
    private IamCsrfTokenRepository csrfTokenRepository;

    @MockitoBean
    private IamSecurityExceptionHandler securityExceptionHandler;

    @Test
    void startsApplicationContext() {
        assertThat(context).isNotNull();
        assertThat(context.isActive()).isTrue();
        assertThat(context.getBean(ImAuditApplication.class)).isNotNull();
        for (Class<?> beanType : new Class<?>[] {
                AuditIngestionAppService.class, AuditQueryAppService.class, AuditExportAppService.class,
                AuditIngestionController.class, AuditQueryController.class, AuditExportController.class
        }) {
            assertThat(mockingDetails(context.getBean(beanType)).isMock()).isFalse();
        }
    }

    @Test
    void disablesExternalInfrastructure() {
        for (String property : new String[] {
                "spring.cloud.nacos.discovery.enabled", "spring.cloud.nacos.config.enabled",
                "spring.cloud.discovery.enabled", "im.iam.enabled", "im.audit.enabled",
                "im.datasource.sharding.enabled", "spring.cloud.stream.enabled"
        }) {
            assertThat(context.getEnvironment().getProperty(property, Boolean.class))
                    .as(property).isFalse();
        }
        assertThat(context.getEnvironment().getProperty("spring.cloud.function.definition")).isEmpty();
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(context.getBeansOfType(RedisConnectionFactory.class)).isEmpty();
        assertThat(context.getBeansOfType(KafkaAdmin.class)).isEmpty();
        // The factory is inert until a binding is requested; neither direction may be bound.
        BindingService bindingService = context.getBean(BindingService.class);
        assertThat(bindingService.getConsumerBindingNames()).isEmpty();
        assertThat(bindingService.getProducerBindingNames()).isEmpty();
    }

    // IAM SDK is disabled, but Audit's own security chains still need its bound properties.
    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IamProperties.class)
    static class SecurityProperties {
    }
}
