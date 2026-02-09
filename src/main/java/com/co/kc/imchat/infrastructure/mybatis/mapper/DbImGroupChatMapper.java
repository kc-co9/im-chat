package com.co.kc.imchat.infrastructure.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.co.kc.imchat.infrastructure.mybatis.entity.DbImGroupChat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DbImGroupChatMapper extends BaseMapper<DbImGroupChat> {
    List<DbImGroupChat> selectListByUserId(@Param("userId") Long userId);
}