package com.co.kc.imchat.management.audit.infrastructure.config.beans;

import com.co.kc.imchat.management.audit.application.AuditIngestionAppService;
import com.co.kc.imchat.management.audit.application.AuditExportAppService;
import com.co.kc.imchat.management.audit.application.AuditQueryAppService;
import com.co.kc.imchat.management.audit.domain.repository.AuditEventRepository;
import com.co.kc.imchat.management.audit.interfaces.mq.AuditEventConsumer;
import com.co.kc.imchat.management.audit.infrastructure.config.AuditPermissionCatalog;
import com.co.kc.imchat.management.audit.model.cqrs.event.AuditIngestEvent;
import com.co.kc.imchat.management.audit.sdk.model.AuditEvent;
import com.co.kc.imchat.management.audit.transformer.application.AuditIngestionTransformer;
import com.co.kc.imchat.management.iam.sdk.catalog.IamPermissionCatalog;
import com.co.kc.imchat.plugin.excel.core.ExcelTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Audit 应用服务与 Kafka 输入 Binding Bean 配置。
 */
@Configuration(proxyBeanMethods = false)
public class AuditServiceBeans {

    @Bean
    public IamPermissionCatalog auditPermissionCatalog() {
        return new AuditPermissionCatalog();
    }

    @Bean
    public AuditIngestionAppService auditIngestionAppService(
            AuditEventRepository auditEventRepository
    ) {
        return new AuditIngestionAppService(
                auditEventRepository,
                AuditIngestionTransformer.INSTANCE);
    }

    @Bean
    public AuditEventConsumer auditEventConsumer(
            AuditIngestionAppService auditIngestionAppService
    ) {
        return new AuditEventConsumer(auditIngestionAppService);
    }

    @Bean
    public AuditQueryAppService auditQueryAppService(
            AuditEventRepository auditEventRepository
    ) {
        return new AuditQueryAppService(auditEventRepository);
    }

    @Bean
    public AuditExportAppService auditExportAppService(
            AuditEventRepository auditEventRepository,
            ExcelTemplate excelTemplate
    ) {
        return new AuditExportAppService(
                auditEventRepository,
                excelTemplate);
    }

    @Bean
    public Consumer<AuditEvent> imAdminAudit(AuditEventConsumer consumer) {
        return event -> {
            AuditIngestEvent ingestEvent = AuditIngestionTransformer.INSTANCE
                    .auditIngestEventFrom("imAdmin", event);
            consumer.accept(ingestEvent);
        };
    }

    @Bean
    public Consumer<AuditEvent> imIamAudit(AuditEventConsumer consumer) {
        return event -> {
            AuditIngestEvent ingestEvent = AuditIngestionTransformer.INSTANCE
                    .auditIngestEventFrom("imIam", event);
            consumer.accept(ingestEvent);
        };
    }

    @Bean
    public Consumer<AuditEvent> imMonitorAudit(AuditEventConsumer consumer) {
        return event -> {
            AuditIngestEvent ingestEvent = AuditIngestionTransformer.INSTANCE
                    .auditIngestEventFrom("imMonitor", event);
            consumer.accept(ingestEvent);
        };
    }

    @Bean
    public Consumer<AuditEvent> imAuditAudit(AuditEventConsumer consumer) {
        return event -> {
            AuditIngestEvent ingestEvent = AuditIngestionTransformer.INSTANCE.auditIngestEventFrom(
                    "imAudit",
                    event);
            consumer.accept(ingestEvent);
        };
    }
}
