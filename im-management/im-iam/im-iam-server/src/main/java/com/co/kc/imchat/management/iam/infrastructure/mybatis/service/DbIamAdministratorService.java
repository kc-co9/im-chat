package com.co.kc.imchat.management.iam.infrastructure.mybatis.service;

import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamAdministrator;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamAdministratorMapper;
import com.co.kc.imchat.plugin.datasource.dao.BaseMybatisService;
import org.springframework.stereotype.Service;

/** IAM 管理员表 MyBatis 服务。 */
@Service
public class DbIamAdministratorService
        extends BaseMybatisService<DbIamAdministratorMapper, DbIamAdministrator> {
}
