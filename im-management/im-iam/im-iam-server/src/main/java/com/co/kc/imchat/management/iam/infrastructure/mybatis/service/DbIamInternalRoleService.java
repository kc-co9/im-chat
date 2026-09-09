package com.co.kc.imchat.management.iam.infrastructure.mybatis.service;

import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamInternalRole;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamInternalRoleMapper;
import com.co.kc.imchat.plugin.datasource.dao.BaseMybatisService;
import org.springframework.stereotype.Service;

/** IAM 内部角色表 MyBatis 服务。 */
@Service
public class DbIamInternalRoleService
        extends BaseMybatisService<DbIamInternalRoleMapper, DbIamInternalRole> {
}
