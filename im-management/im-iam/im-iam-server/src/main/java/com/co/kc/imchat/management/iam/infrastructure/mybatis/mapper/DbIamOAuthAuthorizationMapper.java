package com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamOAuthAuthorization;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DbIamOAuthAuthorizationMapper extends BaseMapper<DbIamOAuthAuthorization> {
}
