package com.co.kc.imchat.management.audit.infrastructure.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.co.kc.imchat.management.audit.infrastructure.mybatis.enums.DbAuditOutcome;
import com.co.kc.imchat.management.audit.infrastructure.mybatis.enums.DbAuditType;
import com.co.kc.imchat.plugin.datasource.dao.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.Map;

/** 审计事实数据库实体。 */
@Data
@TableName(value = "db_audit_event", autoResultMap = true)
@EqualsAndHashCode(callSuper = true)
public class DbAuditEvent extends BaseEntity {
    private String auditId;
    private DbAuditType type;
    private String sourceApp;
    @TableField("action")
    private String actionCode;
    private String actorType;
    private String actorId;
    private String actorName;
    private String targetType;
    private String targetId;
    private DbAuditOutcome outcome;
    private String errorCode;
    private String description;
    private String clientAddress;
    private String userAgent;
    private String traceId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> attributes;
    private Instant occurredAt;
}
