package com.co.kc.imchat.management.audit.infrastructure.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.co.kc.imchat.management.audit.infrastructure.mybatis.entity.DbAuditEvent;
import org.apache.ibatis.annotations.Mapper;

/** 审计事实 Mapper。 */
@Mapper
public interface DbAuditEventMapper extends BaseMapper<DbAuditEvent> {
}
