package com.co.kc.imchat.management.audit.infrastructure.config.beans;

import com.co.kc.imchat.management.audit.domain.repository.AuditEventRepository;
import com.co.kc.imchat.management.audit.infrastructure.domain.repository.MysqlAuditEventRepository;
import com.co.kc.imchat.management.audit.infrastructure.mybatis.service.DbAuditEventService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Audit 持久化组件 Bean 配置。 */
@Configuration(proxyBeanMethods = false)
public class AuditRepositoryBeans {

    @Bean
    public DbAuditEventService dbAuditEventService() {
        return new DbAuditEventService();
    }

    @Bean
    public AuditEventRepository auditEventRepository(DbAuditEventService auditEventService) {
        return new MysqlAuditEventRepository(auditEventService);
    }
}
