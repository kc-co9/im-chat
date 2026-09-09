package com.co.kc.imchat.management.iam.infrastructure.mybatis.service;

import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamApplicationRoleMapper;
import com.co.kc.imchat.plugin.datasource.dao.BaseMybatisService;
import org.springframework.stereotype.Service;

/** IAM 角色表 MyBatis 服务。 */
@Service
public class DbIamApplicationRoleService extends BaseMybatisService<DbIamApplicationRoleMapper, DbIamApplicationRole> {
}
