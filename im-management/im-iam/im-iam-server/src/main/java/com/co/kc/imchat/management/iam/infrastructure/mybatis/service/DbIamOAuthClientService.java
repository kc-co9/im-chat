package com.co.kc.imchat.management.iam.infrastructure.mybatis.service;

import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamOAuthClient;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper.DbIamOAuthClientMapper;
import com.co.kc.imchat.plugin.datasource.dao.BaseMybatisService;
import org.springframework.stereotype.Service;

/** IAM 机器客户端表 MyBatis 服务。 */
@Service
public class DbIamOAuthClientService
        extends BaseMybatisService<DbIamOAuthClientMapper, DbIamOAuthClient> {
}
