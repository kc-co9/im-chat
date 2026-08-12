package com.co.kc.imchat.service.message.infrastructure.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImGroupInboxMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DbImGroupInboxMessageMapper extends BaseMapper<DbImGroupInboxMessage> {
}
