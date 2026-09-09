package com.co.kc.imchat.management.iam.infrastructure.mybatis.service;

import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationRolePermission;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamApplicationRolePermissionMapper;
import com.co.kc.imchat.plugin.datasource.dao.BaseMybatisService;
import org.springframework.stereotype.Service;

/** IAM 角色权限关联表 MyBatis 服务。 */
@Service
public class DbIamApplicationRolePermissionService
        extends BaseMybatisService<DbIamApplicationRolePermissionMapper, DbIamApplicationRolePermission> {
}
