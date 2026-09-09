package com.co.kc.imchat.management.audit.infrastructure.mybatis.service;

import com.co.kc.imchat.management.audit.infrastructure.mybatis.entity.DbAuditEvent;
import com.co.kc.imchat.management.audit.infrastructure.mybatis.mapper.DbAuditEventMapper;
import com.co.kc.imchat.plugin.datasource.dao.BaseMybatisService;

/** 审计事实表 MyBatis 服务。 */
public class DbAuditEventService extends BaseMybatisService<DbAuditEventMapper, DbAuditEvent> {
}
