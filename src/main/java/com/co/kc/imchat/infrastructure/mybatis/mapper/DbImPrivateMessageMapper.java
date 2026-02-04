package com.co.kc.imchat.infrastructure.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImPrivateMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DbImPrivateMessageMapper extends BaseMapper<DbImPrivateMessage> {

}