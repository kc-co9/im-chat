package com.kim.omgchat.infrastructure.mybatis.service;

import com.kim.omgchat.infrastructure.mybatis.entity.DbUser;
import com.kim.omgchat.infrastructure.mybatis.mapper.DbUserMapper;
import org.springframework.stereotype.Service;

/**
 * 用户表(DbUser)表服务接口
 *
 * @author kc
 * @since 2026-02-03 11:11:22
 */
@Service
public class DbUserService extends BaseMybatisService<DbUserMapper, DbUser> {

}
