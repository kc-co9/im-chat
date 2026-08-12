package com.co.kc.imchat.service.account.infrastructure.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.co.kc.imchat.service.account.infrastructure.mybatis.entity.DbUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DbUserMapper extends BaseMapper<DbUser> {

}