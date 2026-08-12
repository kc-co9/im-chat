package com.co.kc.imchat.service.message.infrastructure.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.co.kc.imchat.service.message.infrastructure.mybatis.entity.DbImPrivateChat;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DbImPrivateChatMapper extends BaseMapper<DbImPrivateChat> {

}