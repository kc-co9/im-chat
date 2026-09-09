package com.co.kc.imchat.management.audit.model.cqrs.dto;

import com.co.kc.imchat.management.audit.domain.model.AuditOutcome;
import com.co.kc.imchat.management.audit.domain.model.AuditType;
import com.co.kc.imchat.plugin.excel.convert.EnumNameConverter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.fesod.sheet.annotation.ExcelProperty;

/**
 * 审计 Excel 导出对象，不包含数据库内部字段。
 */
@Getter
@AllArgsConstructor
@SuppressWarnings("ClassCanBeRecord")
public class AuditExportDTO {
    @ExcelProperty(value = "审计标识", index = 0)
    private final String auditId;
    @ExcelProperty(value = "来源应用", index = 1)
    private final String sourceApp;
    @ExcelProperty(value = "类别", index = 2, converter = EnumNameConverter.class)
    private final AuditType type;
    @ExcelProperty(value = "动作", index = 3)
    private final String action;
    @ExcelProperty(value = "结果", index = 4, converter = EnumNameConverter.class)
    private final AuditOutcome outcome;
    @ExcelProperty(value = "操作者类型", index = 5)
    private final String actorType;
    @ExcelProperty(value = "操作者标识", index = 6)
    private final String actorId;
    @ExcelProperty(value = "操作者名称", index = 7)
    private final String actorName;
    @ExcelProperty(value = "目标类型", index = 8)
    private final String targetType;
    @ExcelProperty(value = "目标标识", index = 9)
    private final String targetId;
    @ExcelProperty(value = "错误码", index = 10)
    private final String errorCode;
    @ExcelProperty(value = "说明", index = 11)
    private final String description;
    @ExcelProperty(value = "客户端地址", index = 12)
    private final String clientAddress;
    @ExcelProperty(value = "User-Agent", index = 13)
    private final String userAgent;
    @ExcelProperty(value = "Trace ID", index = 14)
    private final String traceId;
    @ExcelProperty(value = "扩展属性", index = 15)
    private final String attributes;
    @ExcelProperty(value = "发生时间", index = 16)
    private final String occurredAt;
}
