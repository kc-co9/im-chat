package com.co.kc.imchat.management.iam.infrastructure.mybatis.service;

import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationAdministratorRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamApplicationAdministratorRoleMapper;
import com.co.kc.imchat.plugin.datasource.dao.BaseMybatisService;
import org.springframework.stereotype.Service;

/**
 * IAM 管理员角色关联表 MyBatis 服务。
 */
@Service
public class DbIamApplicationAdministratorRoleService extends BaseMybatisService<DbIamApplicationAdministratorRoleMapper, DbIamApplicationAdministratorRole> {
}
