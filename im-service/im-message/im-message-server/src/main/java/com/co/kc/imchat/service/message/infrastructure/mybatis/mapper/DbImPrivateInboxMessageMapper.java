package com.co.kc.imchat.service.message.infrastructure.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImPrivateInboxMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DbImPrivateInboxMessageMapper extends BaseMapper<DbImPrivateInboxMessage> {
}
