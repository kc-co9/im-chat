package com.co.kc.imchat.management.iam.infrastructure.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.co.kc.imchat.management.iam.infrastructure.mybatis.entity.DbIamInternalAdministratorRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DbIamInternalAdministratorRoleMapper
        extends BaseMapper<DbIamInternalAdministratorRole> {
    Long countActive(@Param("roleId") Long roleId);
}
