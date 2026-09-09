package com.co.kc.imchat.management.audit.transformer.application;

import com.co.kc.imchat.management.audit.domain.model.AuditEvent;
import com.co.kc.imchat.management.audit.model.cqrs.dto.AuditExportDTO;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

/** 审计事实到 Excel 导出 DTO 的应用转换器。 */
public final class AuditExportAppTransformer {
    public static final AuditExportAppTransformer INSTANCE = new AuditExportAppTransformer();

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AuditExportAppTransformer() {
    }

    /**
     * 将审计事实转换为指定时区下的安全导出 DTO。
     *
     * @param event 审计事实
     * @param timeZone 展示时区
     * @return Excel 导出 DTO
     */
    public AuditExportDTO auditExportDtoFrom(AuditEvent event, ZoneId timeZone) {
        String attributes = event.getAttributes().values().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
        return new AuditExportDTO(
                safeText(event.getId().value()),
                safeText(event.getSourceApp().value()),
                event.getType(),
                safeText(event.getAction().value()),
                event.getOutcome(),
                safeText(event.getActor().type()),
                safeText(event.getActor().id()),
                safeText(event.getActor().name()),
                safeText(event.getTarget().type()),
                safeText(event.getTarget().id()),
                safeText(event.getErrorCode() == null ? null : event.getErrorCode().value()),
                safeText(event.getDescription().value()),
                safeText(event.getClientContext() == null ? null : event.getClientContext().address()),
                safeText(event.getClientContext() == null ? null : event.getClientContext().userAgent()),
                safeText(event.getTraceId() == null ? null : event.getTraceId().value()),
                safeText(attributes),
                TIME_FORMATTER.format(event.getOccurredAt().atZone(timeZone)));
    }

    private String safeText(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return switch (value.charAt(0)) {
            case '=', '+', '-', '@' -> "'" + value;
            default -> value;
        };
    }
}
