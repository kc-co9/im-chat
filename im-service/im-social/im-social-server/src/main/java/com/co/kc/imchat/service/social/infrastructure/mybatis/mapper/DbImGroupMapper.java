package com.co.kc.imchat.service.social.infrastructure.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.co.kc.imchat.service.social.infrastructure.mybatis.entity.DbImGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DbImGroupMapper extends BaseMapper<DbImGroup> {
    List<DbImGroup> selectByUserId(@Param("userId") Long userId);
}
