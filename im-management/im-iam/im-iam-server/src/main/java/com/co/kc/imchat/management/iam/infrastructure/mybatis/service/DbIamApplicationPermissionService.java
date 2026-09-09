package com.co.kc.imchat.management.iam.infrastructure.mybatis.service;

import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApplicationPermission;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamApplicationPermissionMapper;
import com.co.kc.imchat.plugin.datasource.dao.BaseMybatisService;
import org.springframework.stereotype.Service;

/** IAM 权限表 MyBatis 服务。 */
@Service
public class DbIamApplicationPermissionService
        extends BaseMybatisService<DbIamApplicationPermissionMapper, DbIamApplicationPermission> {
}
