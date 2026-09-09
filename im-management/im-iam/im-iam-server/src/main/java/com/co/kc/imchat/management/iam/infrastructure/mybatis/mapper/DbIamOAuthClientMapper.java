package com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamOAuthClient;
import org.apache.ibatis.annotations.Mapper;

/** IAM 机器客户端 Mapper。 */
@Mapper
public interface DbIamOAuthClientMapper extends BaseMapper<DbIamOAuthClient> {
}
