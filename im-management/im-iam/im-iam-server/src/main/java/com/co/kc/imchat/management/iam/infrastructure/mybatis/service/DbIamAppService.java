package com.co.kc.imchat.management.iam.infrastructure.mybatis.service;

import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamApp;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamAppMapper;
import com.co.kc.imchat.plugin.datasource.dao.BaseMybatisService;
import org.springframework.stereotype.Service;

/** IAM 应用表 MyBatis 服务。 */
@Service
public class DbIamAppService
        extends BaseMybatisService<DbIamAppMapper, DbIamApp> {
}
