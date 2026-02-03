package com.kim.omgchat.infrastructure.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kim.omgchat.infrastructure.mybatis.entity.DbFriend;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DbFriendMapper extends BaseMapper<DbFriend> {
}