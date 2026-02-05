package com.co.kc.imchat.infrastructure.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImChat;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DbImChatMapper extends BaseMapper<DbImChat> {
}