package com.co.kc.imchat.service.account.infrastructure.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.co.kc.imchat.service.account.infrastructure.mybatis.entity.DbUser;
import com.co.kc.imchat.service.account.infrastructure.mybatis.query.DbUserQueryCondition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DbUserMapper extends BaseMapper<DbUser> {

    /**
     * 分页查询已逻辑删除的管理端用户。
     *
     * @param page      MyBatis-Plus 分页参数
     * @param condition 管理端查询条件
     * @return 已删除用户分页结果
     */
    IPage<DbUser> selectRawUsers(Page<DbUser> page, @Param("condition") DbUserQueryCondition condition);

    /**
     * 按用户 ID 查询管理端用户，包括已逻辑删除的用户。
     *
     * @param userId 用户 ID
     * @return 用户持久化记录，不存在时返回 {@code null}
     */
    DbUser selectRawByUserId(@Param("userId") Long userId);
}
